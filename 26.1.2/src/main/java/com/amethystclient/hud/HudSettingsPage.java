package com.amethystclient.hud;

import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.hud.setting.ModeSetting;
import com.amethystclient.hud.setting.Setting;
import com.amethystclient.hud.setting.SliderSetting;
import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Icons;
import com.amethystclient.ui.Motion;
import com.amethystclient.ui.Page;
import com.amethystclient.ui.Ui;
import com.amethystclient.ui.widget.FlatButton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.lwjgl.glfw.GLFW;

/**
 * The HUD settings, opened with Right Shift, laid out as a ClickGUI:
 * <ul>
 *   <li>one draggable column per category, each with an icon header and a scrolling list of
 *   modules. Left click turns a module on/off (its name turns accent), right click (or the dot)
 *   opens its settings: sliders, option lists and check/cross switches;</li>
 *   <li>a Themes panel in the bottom-right corner;</li>
 *   <li>a layout mode, where the HUD modules can be dragged around the screen.</li>
 * </ul>
 */
public class HudSettingsPage extends Page {
	private static final int COLUMN_WIDTH = 136;
	private static final int COLUMN_GAP = 10;
	private static final int HEADER = 24;
	private static final int RADIUS = 6;
	private static final int ROW_HEIGHT = 17;
	private static final int ROW_GAP = 5;
	private static final int ROW_INSET = 6;
	private static final int SETTING_PAD = 8;
	private static final float SMALL = 0.8f;
	private static final float TITLE = 1.2f;
	private static final int THEMES_WIDTH = 118;
	private static final int THEME_ROW = 14;
	private static final int SNAP = 4;

	/** A module row: a HUD module, or one of the client options. */
	private static final class Entry {
		final String name;
		final Supplier<Boolean> on;
		/** Left-click action; null when left click just opens the settings. */
		final Runnable click;
		final List<Setting> settings;
		final Motion expand = new Motion(0f, 150);
		boolean expanded;

		Entry(String name, Supplier<Boolean> on, Runnable click, List<Setting> settings) {
			this.name = name;
			this.on = on;
			this.click = click;
			this.settings = settings;
		}
	}

	private static final class Column {
		final String id;
		final String title;
		final String[] icon;
		final List<Entry> entries;
		int x;
		int y;
		double scroll;
		int contentHeight;

		Column(String id, String title, String[] icon, List<Entry> entries) {
			this.id = id;
			this.title = title;
			this.icon = icon;
			this.entries = entries;
		}
	}

	/** A clickable area from the last frame. */
	private record Hit(int x1, int y1, int x2, int y2, Runnable left, Runnable right, SliderSetting slider, int trackX, int trackW) {
		boolean contains(double mx, double my) {
			return mx >= x1 && mx < x2 && my >= y1 && my < y2;
		}
	}

	private final HudConfig config = HudConfig.get();
	private final Motion open = new Motion(0f, 160);
	private final Motion layoutFade = new Motion(0f, 160);
	private final List<Column> columns = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();
	private final FlatButton layoutDone;

	private boolean layoutMode;
	private int columnHeight;
	private double lastMouseX;
	private double lastMouseY;

	// Dragging: a column by its header, a slider, or (layout mode) a HUD module.
	private Column draggingColumn;
	private int grabColumnX;
	private int grabColumnY;
	private Hit draggingSlider;
	private HudModule draggingModule;
	private double grabX;
	private double grabY;
	private boolean snappedCenterX;
	private boolean snappedCenterY;

	public HudSettingsPage() {
		columns.add(new Column("info", "Info", Icons.CHART, moduleEntries("fps", "ping", "coords", "cps")));
		columns.add(new Column("player", "Player", Icons.PLAYER, moduleEntries("armor", "keystrokes")));
		columns.add(new Column("client", "Client", Icons.GEAR, List.of(
				new Entry("Edit layout", () -> false, () -> setLayoutMode(true), List.of()),
				new Entry("HUD style", () -> false, null, List.of(
						new SliderSetting("Background", 0, 1, 0.05, () -> config.backgroundOpacity,
								v -> config.backgroundOpacity = v, v -> Math.round(v * 100) + "%"),
						new SliderSetting("Scale", 0.5, 2, 0.05, () -> config.scale,
								v -> config.scale = v, v -> Math.round(v * 100) + "%"))),
				new Entry("Reduce motion", () -> config.reduceMotion, () -> config.reduceMotion = !config.reduceMotion, List.of()),
				new Entry("Reset layout", () -> false, this::resetLayout, List.of()))));
		layoutDone = new FlatButton("Done", FlatButton.Variant.PRIMARY, () -> setLayoutMode(false));
		open.set(1f);
	}

	private static List<Entry> moduleEntries(String... ids) {
		List<Entry> entries = new ArrayList<>();
		for (String id : ids) {
			for (HudModule module : AmethystHud.MODULES) {
				if (module.id.equals(id)) {
					HudConfig.Module settings = module.settings();
					entries.add(new Entry(module.name, () -> settings.enabled, () -> settings.enabled = !settings.enabled,
							module.settingsList));
				}
			}
		}
		return entries;
	}

	private void setLayoutMode(boolean on) {
		layoutMode = on;
		layoutFade.set(on ? 1f : 0f);
		draggingModule = null;
		draggingColumn = null;
		draggingSlider = null;
	}

	private void resetLayout() {
		for (HudModule module : AmethystHud.MODULES) {
			module.resetPosition();
		}
		config.panels.clear();
	}

	@Override
	public void removed() {
		config.save();
	}

	// ---- layout ----

	private void placeColumns() {
		columnHeight = Math.max(HEADER + 60, Math.min(250, height - 60));
		int total = columns.size() * COLUMN_WIDTH + (columns.size() - 1) * COLUMN_GAP;
		int x = (width - total) / 2;
		int y = Math.max(10, (height - columnHeight) / 2 - 16);
		for (Column column : columns) {
			int[] saved = config.panels.get(column.id);
			boolean valid = saved != null && saved.length == 2;
			column.x = Math.clamp(valid ? saved[0] : x, 0, Math.max(0, width - COLUMN_WIDTH));
			column.y = Math.clamp(valid ? saved[1] : y, 0, Math.max(0, height - HEADER));
			x += COLUMN_WIDTH + COLUMN_GAP;
		}
	}

	/** Option indices of a mode setting, wrapped into lines that fit in {@code width}. */
	private static List<List<Integer>> wrap(Draw d, ModeSetting mode, int width) {
		List<List<Integer>> lines = new ArrayList<>();
		List<Integer> line = new ArrayList<>();
		int used = 0;
		int space = Ui.width(d, " ", SMALL) + 2;
		for (int i = 0; i < mode.options.size(); i++) {
			int w = Ui.width(d, mode.options.get(i), SMALL);
			if (!line.isEmpty() && used + space + w > width) {
				lines.add(line);
				line = new ArrayList<>();
				used = 0;
			}
			used += (line.isEmpty() ? 0 : space) + w;
			line.add(i);
		}
		if (!line.isEmpty()) {
			lines.add(line);
		}
		return lines;
	}

	private static int settingHeight(Draw d, Setting setting, int innerWidth) {
		return switch (setting) {
			case BoolSetting b -> 11;
			case SliderSetting s -> 20;
			case ModeSetting m -> 10 + wrap(d, m, innerWidth - 10).size() * 8 + 6 + 4;
		};
	}

	private static int settingsHeight(Draw d, Entry entry, int innerWidth) {
		if (entry.settings.isEmpty()) {
			return 0;
		}
		int h = 3;
		for (Setting setting : entry.settings) {
			h += settingHeight(d, setting, innerWidth);
		}
		return h + 2;
	}

	// ---- rendering ----

	@Override
	public void render(Draw d, int mx, int my) {
		AmethystTheme t = AmethystTheme.current();
		AmethystHud.renderModules(d, true);
		d.layer();

		float fade = layoutFade.get();
		if (fade < 1f) {
			hits.clear();
			if (layoutMode) {
				mx = my = -1;
			}
			d.alpha(open.get() * (1f - fade));
			d.fill(0, 0, width, height, 0x59000000);
			placeColumns();
			for (Column column : columns) {
				renderColumn(d, t, column, mx, my);
			}
			renderThemes(d, t, mx, my);
			d.alpha(1f);
		}
		if (fade > 0f) {
			renderLayoutOverlay(d, t, mx, my, fade);
		}
	}

	private void renderColumn(Draw d, AmethystTheme t, Column c, int mx, int my) {
		int x = c.x;
		int y = c.y;
		int bodyTop = y + HEADER;
		int bottom = y + columnHeight;

		// Header (icon + title) on a slightly darker strip, then the body.
		Ui.round(d, x, y, COLUMN_WIDTH, HEADER, RADIUS, AmethystTheme.withAlpha(t.bgSidebar(), 0.96f), true, false);
		Ui.round(d, x, bodyTop, COLUMN_WIDTH, columnHeight - HEADER, RADIUS, AmethystTheme.withAlpha(t.bg(), 0.88f), false, true);
		int titleW = Ui.width(d, c.title, TITLE);
		int iconX = x + (COLUMN_WIDTH - (9 + 5 + titleW)) / 2;
		Ui.icon(d, c.icon, iconX, y + (HEADER - 9) / 2, t.accent());
		Ui.text(d, c.title, iconX + 14, y + (HEADER - 9 * TITLE) / 2 + 1, AmethystTheme.TEXT_PRIMARY, TITLE);
		addHit(x, y, x + COLUMN_WIDTH, bodyTop, y, bodyTop, () -> {
			draggingColumn = c;
			grabColumnX = (int) lastMouseX - c.x;
			grabColumnY = (int) lastMouseY - c.y;
		}, null, null, 0, 0);

		// Rows, scrolling inside the body.
		int clipTop = bodyTop + 1;
		int clipBottom = bottom - 4;
		boolean mouseInBody = mx >= x && mx < x + COLUMN_WIDTH && my >= clipTop && my < clipBottom;
		int rmx = mouseInBody ? mx : -1;
		int rmy = mouseInBody ? my : -1;
		int rowX = x + ROW_INSET;
		int rowW = COLUMN_WIDTH - ROW_INSET * 2;
		int innerW = rowW - SETTING_PAD * 2;

		d.clip(x, clipTop, x + COLUMN_WIDTH, clipBottom);
		int top = bodyTop + ROW_INSET - (int) c.scroll;
		int rowY = top;
		for (Entry entry : c.entries) {
			entry.expand.set(entry.expanded ? 1f : 0f);
			int h = ROW_HEIGHT + Math.round(settingsHeight(d, entry, innerW) * entry.expand.get());
			renderRow(d, t, entry, rowX, rowY, rowW, h, innerW, rmx, rmy, clipTop, clipBottom);
			rowY += h + ROW_GAP;
		}
		d.unclip();
		c.contentHeight = rowY - top + ROW_INSET - ROW_GAP;
		c.scroll = Math.clamp(c.scroll, 0, maxScroll(c));

		// A thin scrollbar when the list doesn't fit.
		double max = maxScroll(c);
		if (max > 0) {
			int track = clipBottom - clipTop - 6;
			int thumb = Math.max(14, (int) (track * track / (track + max)));
			int ty = clipTop + 3 + (int) ((track - thumb) * (c.scroll / max));
			Ui.round(d, x + COLUMN_WIDTH - 3, ty, 2, thumb, 1, AmethystTheme.withAlpha(t.accent(), 0.4f));
		}
	}

	private double maxScroll(Column c) {
		return Math.max(0, c.contentHeight - (columnHeight - HEADER - 5));
	}

	private void renderRow(Draw d, AmethystTheme t, Entry entry, int x, int y, int w, int h, int innerW,
			int mx, int my, int clipTop, int clipBottom) {
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + ROW_HEIGHT;
		Ui.round(d, x, y, w, h, 4, hover ? t.bgCard() : AmethystTheme.withAlpha(t.bgSidebar(), 0.9f));

		boolean on = entry.on.get();
		int nameColor = on ? t.accent() : hover ? AmethystTheme.TEXT_HOVER : AmethystTheme.TEXT_LABEL;
		Ui.centered(d, entry.name, x + w / 2, y + (ROW_HEIGHT - d.lineHeight()) / 2 + 1, nameColor);
		Runnable expand = null;
		if (!entry.settings.isEmpty()) {
			// Green: settings open, red: closed.
			Ui.round(d, x + w - 9, y + ROW_HEIGHT / 2 - 1, 3, 3, 1, entry.expanded ? AmethystTheme.SUCCESS_TEXT : AmethystTheme.DANGER);
			expand = () -> entry.expanded = !entry.expanded;
			addHit(x + w - 14, y, x + w, y + ROW_HEIGHT, clipTop, clipBottom, expand, expand, null, 0, 0);
		}
		addHit(x, y, x + w, y + ROW_HEIGHT, clipTop, clipBottom, entry.click != null ? entry.click : expand, expand, null, 0, 0);

		if (h <= ROW_HEIGHT) {
			return;
		}
		// Settings, clipped to the row while it opens or closes.
		int clipStart = Math.max(y + ROW_HEIGHT, clipTop);
		int clipEnd = Math.min(y + h, clipBottom);
		if (clipEnd <= clipStart) {
			return;
		}
		d.clip(x, clipStart, x + w, clipEnd);
		int ix = x + SETTING_PAD;
		int sy = y + ROW_HEIGHT + 3;
		for (Setting setting : entry.settings) {
			if (sy >= clipEnd) {
				break;
			}
			switch (setting) {
				case BoolSetting b -> renderBool(d, b, ix, sy, innerW, mx, my, clipStart, clipEnd);
				case SliderSetting s -> renderSlider(d, t, s, ix, sy, innerW, clipStart, clipEnd);
				case ModeSetting m -> renderMode(d, t, m, ix, sy, innerW, mx, my, clipStart, clipEnd);
			}
			sy += settingHeight(d, setting, innerW);
		}
		d.unclip();
	}

	private void renderBool(Draw d, BoolSetting b, int x, int y, int w, int mx, int my, int clipTop, int clipBottom) {
		boolean on = b.get();
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + 10;
		Ui.icon(d, on ? Icons.CHECK : Icons.CROSS, x + 1, y + 1, on ? AmethystTheme.SUCCESS_TEXT : AmethystTheme.DANGER);
		Ui.text(d, b.name, x + 12, y + 1.5f, on || hover ? AmethystTheme.TEXT_PRIMARY : AmethystTheme.TEXT_LABEL, SMALL);
		addHit(x, y, x + w, y + 10, clipTop, clipBottom, b::toggle, null, null, 0, 0);
	}

	private void renderSlider(Draw d, AmethystTheme t, SliderSetting s, int x, int y, int w, int clipTop, int clipBottom) {
		Ui.text(d, s.name + ":", x, y, AmethystTheme.TEXT_PRIMARY, SMALL);
		String value = s.display();
		Ui.text(d, value, x + w - Ui.width(d, value, SMALL), y, AmethystTheme.TEXT_PRIMARY, SMALL);
		int by = y + 11;
		int filled = (int) Math.round(s.fraction() * w);
		Ui.round(d, x, by, w, 3, 1, AmethystTheme.withAlpha(AmethystTheme.WHITE, 0.08f));
		Ui.round(d, x, by, Math.max(3, filled), 3, 1, t.accent());
		int knob = Math.clamp(x + filled, x + 2, x + w - 2);
		Ui.round(d, knob - 2, by - 1, 5, 5, 2, AmethystTheme.WHITE);
		addHit(x - 3, by - 4, x + w + 3, by + 7, clipTop, clipBottom, null, null, s, x, w);
	}

	private void renderMode(Draw d, AmethystTheme t, ModeSetting m, int x, int y, int w, int mx, int my, int clipTop, int clipBottom) {
		Ui.text(d, m.name + ":", x, y, AmethystTheme.TEXT_PRIMARY, SMALL);
		List<List<Integer>> lines = wrap(d, m, w - 10);
		int boxY = y + 10;
		Ui.round(d, x, boxY, w, lines.size() * 8 + 6, 3, AmethystTheme.withAlpha(t.bgSidebar(), 0.9f));
		String selected = m.get();
		int space = Ui.width(d, " ", SMALL) + 2;
		int ly = boxY + 4;
		for (List<Integer> line : lines) {
			int ox = x + 5;
			for (int i : line) {
				String option = m.options.get(i);
				int ow = Ui.width(d, option, SMALL);
				boolean hover = mx >= ox - 1 && mx < ox + ow + 1 && my >= ly - 1 && my < ly + 8;
				int color = option.equals(selected) ? t.accent() : hover ? AmethystTheme.TEXT_HOVER : AmethystTheme.TEXT_MUTED;
				Ui.text(d, option, ox, ly, color, SMALL);
				addHit(ox - 1, ly - 1, ox + ow + 1, ly + 8, clipTop, clipBottom, () -> m.set(option), null, null, 0, 0);
				ox += ow + space;
			}
			ly += 8;
		}
	}

	/** Records a clickable area, cut to the visible part of the list. */
	private void addHit(int x1, int y1, int x2, int y2, int clipTop, int clipBottom, Runnable left, Runnable right,
			SliderSetting slider, int trackX, int trackW) {
		y1 = Math.max(y1, clipTop);
		y2 = Math.min(y2, clipBottom);
		if (y2 > y1) {
			hits.add(new Hit(x1, y1, x2, y2, left, right, slider, trackX, trackW));
		}
	}

	private void renderThemes(Draw d, AmethystTheme t, int mx, int my) {
		List<AmethystTheme> themes = AmethystTheme.ALL;
		int h = 24 + themes.size() * THEME_ROW + 6;
		int x = width - THEMES_WIDTH - 10;
		int y = height - h - 10;
		Ui.round(d, x, y, THEMES_WIDTH, h, RADIUS, AmethystTheme.withAlpha(t.bgSidebar(), 0.94f));
		Ui.round(d, x + 9, y + 10, 2, 2, 1, AmethystTheme.TEXT_PRIMARY);
		Ui.text(d, "Themes", x + 16, y + 7, AmethystTheme.TEXT_PRIMARY, 1.1f);
		int ry = y + 24;
		for (AmethystTheme theme : themes) {
			boolean selected = theme == t;
			boolean hover = mx >= x && mx < x + THEMES_WIDTH && my >= ry && my < ry + THEME_ROW;
			Ui.round(d, x + 10, ry + 3, 7, 7, 2, theme.accent());
			Ui.round(d, x + 19, ry + 3, 7, 7, 2, theme.accentPressed());
			int color = selected ? AmethystTheme.TEXT_HEADLINE : hover ? AmethystTheme.TEXT_HOVER : AmethystTheme.TEXT_LABEL;
			d.text(theme.name(), x + 31, ry + 3, color);
			if (selected) {
				Ui.round(d, x + THEMES_WIDTH - 12, ry + 5, 3, 3, 1, theme.accent());
			}
			hits.add(new Hit(x, ry, x + THEMES_WIDTH, ry + THEME_ROW, () -> config.theme = theme.name(), null, null, 0, 0));
			ry += THEME_ROW;
		}
	}

	private void renderLayoutOverlay(Draw d, AmethystTheme t, int mx, int my, float fade) {
		d.alpha(fade);
		float s = AmethystHud.scale();
		int hudW = AmethystHud.hudWidth(d);
		int hudH = AmethystHud.hudHeight(d);
		d.fill(0, 0, width, height, 0x33000000);

		if (draggingModule != null && snappedCenterX) {
			d.fill(width / 2, 0, width / 2 + 1, height, AmethystTheme.withAlpha(t.accent(), 0.5f));
		}
		if (draggingModule != null && snappedCenterY) {
			d.fill(0, height / 2, width, height / 2 + 1, AmethystTheme.withAlpha(t.accent(), 0.5f));
		}

		HudModule hovered = layoutMode && draggingModule == null ? moduleAt(mx, my) : null;
		for (HudModule module : AmethystHud.MODULES) {
			if (!module.enabled() || module.width == 0) {
				continue;
			}
			int x = Math.round(module.x(hudW) * s) - 2;
			int y = Math.round(module.y(hudH) * s) - 2;
			int w = Math.round(module.width * s) + 4;
			int h = Math.round(module.height * s) + 4;
			int color = module == draggingModule ? t.accent()
					: module == hovered ? AmethystTheme.withAlpha(t.accent(), 0.5f)
					: AmethystTheme.withAlpha(AmethystTheme.WHITE, 0.25f);
			Ui.outline(d, x, y, w, h, Ui.RADIUS_CARD, color);
			if (module == hovered || module == draggingModule) {
				int ly = y > 12 ? y - 11 : y + h + 2;
				d.text(module.name, x + 1, ly, color);
			}
		}

		String hint = "Drag modules to move them  ·  Esc when done";
		int hw = d.textWidth(hint) + 16;
		Ui.round(d, (width - hw) / 2, 8, hw, 18, RADIUS, AmethystTheme.withAlpha(t.bgSidebar(), 0.94f));
		Ui.centered(d, hint, width / 2, 13, AmethystTheme.TEXT_SECONDARY);
		layoutDone.at((width - 64) / 2, 32, 64, 18);
		layoutDone.enabled = layoutMode;
		layoutDone.render(d, t, layoutMode ? mx : -1, layoutMode ? my : -1);
		d.alpha(1f);
	}

	/** The topmost enabled module under the mouse (GUI pixels), or null. */
	private HudModule moduleAt(double mx, double my) {
		float s = AmethystHud.scale();
		int hudW = (int) (width / s);
		int hudH = (int) (height / s);
		double hx = mx / s;
		double hy = my / s;
		for (int i = AmethystHud.MODULES.size() - 1; i >= 0; i--) {
			HudModule module = AmethystHud.MODULES.get(i);
			if (!module.enabled() || module.width == 0) {
				continue;
			}
			int x = module.x(hudW);
			int y = module.y(hudH);
			if (hx >= x - 2 && hx < x + module.width + 2 && hy >= y - 2 && hy < y + module.height + 2) {
				return module;
			}
		}
		return null;
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		lastMouseX = mx;
		lastMouseY = my;
		if (layoutMode) {
			if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || layoutDone.mouseClicked(mx, my)) {
				return true;
			}
			HudModule module = moduleAt(mx, my);
			if (module != null) {
				float s = AmethystHud.scale();
				draggingModule = module;
				grabX = mx / s - module.x((int) (width / s));
				grabY = my / s - module.y((int) (height / s));
			}
			return true;
		}

		// Later hits are drawn on top (the dot over its row), so search from the end.
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (!hit.contains(mx, my)) {
				continue;
			}
			if (hit.slider() != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				draggingSlider = hit;
				hit.slider().setFraction((mx - hit.trackX()) / hit.trackW());
			} else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hit.left() != null) {
				hit.left().run();
			} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hit.right() != null) {
				hit.right().run();
			}
			return true;
		}
		return true;
	}

	@Override
	public void mouseDragged(double mx, double my, int button) {
		lastMouseX = mx;
		lastMouseY = my;
		if (draggingSlider != null) {
			draggingSlider.slider().setFraction((mx - draggingSlider.trackX()) / draggingSlider.trackW());
			return;
		}
		if (draggingColumn != null) {
			int x = Math.clamp((int) mx - grabColumnX, 0, Math.max(0, width - COLUMN_WIDTH));
			int y = Math.clamp((int) my - grabColumnY, 0, Math.max(0, height - HEADER));
			config.panels.put(draggingColumn.id, new int[] {x, y});
			return;
		}
		if (draggingModule != null) {
			float s = AmethystHud.scale();
			int hudW = (int) (width / s);
			int hudH = (int) (height / s);
			int x = (int) Math.round(mx / s - grabX);
			int y = (int) Math.round(my / s - grabY);
			int centerX = (hudW - draggingModule.width) / 2;
			int centerY = (hudH - draggingModule.height) / 2;
			snappedCenterX = Math.abs(x - centerX) <= SNAP;
			snappedCenterY = Math.abs(y - centerY) <= SNAP;
			x = snappedCenterX ? centerX : snap(x, hudW - draggingModule.width);
			y = snappedCenterY ? centerY : snap(y, hudH - draggingModule.height);
			draggingModule.moveTo(Math.clamp(x, 0, Math.max(0, hudW - draggingModule.width)),
					Math.clamp(y, 0, Math.max(0, hudH - draggingModule.height)), hudW, hudH);
		}
	}

	/** Snaps to the default margin from either edge. */
	private static int snap(int value, int max) {
		if (Math.abs(value - HudModule.MARGIN) <= SNAP) {
			return HudModule.MARGIN;
		}
		if (Math.abs(value - (max - HudModule.MARGIN)) <= SNAP) {
			return max - HudModule.MARGIN;
		}
		return value;
	}

	@Override
	public void mouseReleased(double mx, double my, int button) {
		layoutDone.mouseReleased(mx, my);
		draggingSlider = null;
		draggingColumn = null;
		draggingModule = null;
		snappedCenterX = false;
		snappedCenterY = false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		if (layoutMode) {
			return true;
		}
		for (Column c : columns) {
			if (mx >= c.x && mx < c.x + COLUMN_WIDTH && my >= c.y && my < c.y + columnHeight) {
				c.scroll = Math.clamp(c.scroll - amount * 14, 0, maxScroll(c));
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(int key) {
		if (key == GLFW.GLFW_KEY_ESCAPE && layoutMode) {
			setLayoutMode(false);
			return true;
		}
		return false;
	}
}
