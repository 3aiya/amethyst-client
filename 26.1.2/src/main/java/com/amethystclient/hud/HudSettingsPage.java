package com.amethystclient.hud;

import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.hud.setting.ListSetting;
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
import java.util.Locale;
import java.util.function.Supplier;
import org.lwjgl.glfw.GLFW;

/**
 * The ClickGUI, opened with Right Shift:
 * <ul>
 *   <li>one draggable column per {@link Category}, built from {@link Modules#ALL}, each with an
 *   icon header and a scrolling list of modules. Left click turns a module on/off (its name turns
 *   accent), right click (or the dot) opens its settings: sliders, option lists, check/cross
 *   switches and the keybind. Middle click binds a key straight away. Right click on a header
 *   folds the column up;</li>
 *   <li>typing anywhere searches every column by name and description;</li>
 *   <li>hovering a module for a moment shows its description;</li>
 *   <li>a Themes panel in the bottom-right corner;</li>
 *   <li>a layout mode, where the HUD modules can be dragged around the screen.</li>
 * </ul>
 */
public class HudSettingsPage extends Page {
	private static final int COLUMN_WIDTH = 142;
	private static final int COLUMN_GAP = 10;
	private static final int MAX_COLUMN_HEIGHT = 262;
	private static final int HEADER = 28;
	private static final int RADIUS = 6;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_GAP = 5;
	private static final int ROW_INSET = 6;
	private static final int ROW_RADIUS = 4;
	private static final int SETTING_PAD = 8;
	private static final int FADE = 10;
	private static final float SMALL = 0.8f;
	private static final float NAME = 1.1f;
	private static final float TITLE = 1.3f;
	private static final int THEMES_WIDTH = 120;
	private static final int THEME_ROW = 15;
	private static final int SEARCH_WIDTH = 170;
	private static final int SEARCH_HEIGHT = 18;
	private static final int TOOLTIP_WIDTH = 150;
	private static final long TOOLTIP_DELAY_MS = 450;
	private static final int SNAP = 4;

	/** A module row: a {@link ClientModule}, or one of the client actions. */
	private static final class Entry {
		final String name;
		final String description;
		/** Null for an action; only modules can be bound to a key. */
		final ClientModule module;
		final Supplier<Boolean> on;
		/** Left-click action; null when left click just opens the settings. */
		final Runnable click;
		final List<Setting> settings;
		final Motion expand = new Motion(0f, 180);
		final Motion hover = new Motion(0f, 120);
		final Motion active;
		boolean expanded;

		Entry(String name, String description, ClientModule module, Supplier<Boolean> on, Runnable click, List<Setting> settings) {
			this.name = name;
			this.description = description;
			this.module = module;
			this.on = on;
			this.click = click;
			this.settings = settings;
			this.active = new Motion(on.get() ? 1f : 0f, 150);
		}

		static Entry of(ClientModule module) {
			return new Entry(module.name, module.description, module, module::enabled, module::toggle, module.settingsList);
		}

		/** Whether it has anything to show when expanded. */
		boolean expandable() {
			return module != null || !settings.isEmpty();
		}

		boolean matches(String query) {
			return query.isEmpty()
					|| name.toLowerCase(Locale.ROOT).contains(query)
					|| description.toLowerCase(Locale.ROOT).contains(query);
		}
	}

	private static final class Column {
		final Category category;
		final List<Entry> entries;
		final Motion collapse;
		int x;
		int y;
		/** Where the list is scrolling to, and where it's drawn (eases towards {@link #scroll}). */
		double scroll;
		double shownScroll;
		int contentHeight;
		int matches;

		Column(Category category, List<Entry> entries, boolean collapsed) {
			this.category = category;
			this.entries = entries;
			this.collapse = new Motion(collapsed ? 1f : 0f, 200);
		}
	}

	/** A clickable area from the last frame. */
	private record Hit(int x1, int y1, int x2, int y2, Runnable left, Runnable right, Runnable middle,
			SliderSetting slider, int trackX, int trackW) {
		boolean contains(double mx, double my) {
			return mx >= x1 && mx < x2 && my >= y1 && my < y2;
		}

		static Hit click(int x1, int y1, int x2, int y2, Runnable left, Runnable right, Runnable middle) {
			return new Hit(x1, y1, x2, y2, left, right, middle, null, 0, 0);
		}
	}

	private final HudConfig config = HudConfig.get();
	private final long openedAt = System.nanoTime();
	private final Motion layoutFade = new Motion(0f, 160);
	private final List<Column> columns = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();
	private final FlatButton layoutDone;

	private boolean layoutMode;
	private int columnHeight;
	private double lastMouseX;
	private double lastMouseY;
	private long lastFrame = System.nanoTime();

	private String query = "";
	/** The module waiting for a key, or null. */
	private Entry binding;
	private Entry hovered;
	private long hoveredSince;

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
		for (Category category : Category.values()) {
			List<Entry> entries = new ArrayList<>();
			for (ClientModule module : Modules.in(category)) {
				entries.add(Entry.of(module));
			}
			if (category == Category.CLIENT) {
				entries.addAll(clientActions());
			}
			if (!entries.isEmpty()) {
				columns.add(new Column(category, entries, config.collapsed.contains(category.id())));
			}
		}
		layoutDone = new FlatButton("Done", FlatButton.Variant.PRIMARY, () -> setLayoutMode(false));
	}

	private List<Entry> clientActions() {
		return List.of(
				new Entry("Edit Layout", "Drag the HUD modules around the screen", null,
						() -> false, () -> setLayoutMode(true), List.of()),
				new Entry("HUD Style", "Background opacity and size of every HUD module", null, () -> false, null, List.of(
						new SliderSetting("Background", 0, 1, 0.05, () -> config.backgroundOpacity,
								v -> config.backgroundOpacity = v, v -> Math.round(v * 100) + "%"),
						new SliderSetting("Scale", 0.5, 2, 0.05, () -> config.scale,
								v -> config.scale = v, v -> Math.round(v * 100) + "%"))),
				new Entry("Reduce Motion", "Turns off the animations", null,
						() -> config.reduceMotion, () -> config.reduceMotion = !config.reduceMotion, List.of()),
				new Entry("Reset Layout", "Puts every HUD module and column back where it started", null,
						() -> false, this::resetLayout, List.of()));
	}

	private void setLayoutMode(boolean on) {
		layoutMode = on;
		layoutFade.set(on ? 1f : 0f);
		draggingModule = null;
		draggingColumn = null;
		draggingSlider = null;
		binding = null;
	}

	private void resetLayout() {
		for (HudModule module : Modules.HUD) {
			module.resetPosition();
		}
		config.panels.clear();
	}

	@Override
	public void removed() {
		config.save();
	}

	// ---- layout ----

	/** Default spots: centred, wrapping into more rows when the screen is too narrow. */
	private void placeColumns() {
		int perRow = Math.clamp((width - 20 + COLUMN_GAP) / (COLUMN_WIDTH + COLUMN_GAP), 1, columns.size());
		int rows = (columns.size() + perRow - 1) / perRow;
		int top = 30;
		int bottom = 24;
		columnHeight = Math.clamp((height - top - bottom - (rows - 1) * COLUMN_GAP) / rows, HEADER + 40, MAX_COLUMN_HEIGHT);
		int totalHeight = rows * columnHeight + (rows - 1) * COLUMN_GAP;
		int startY = Math.max(top, (height - totalHeight) / 2);
		for (int i = 0; i < columns.size(); i++) {
			Column column = columns.get(i);
			int row = i / perRow;
			int inRow = Math.min(perRow, columns.size() - row * perRow);
			int rowWidth = inRow * COLUMN_WIDTH + (inRow - 1) * COLUMN_GAP;
			int x = (width - rowWidth) / 2 + (i % perRow) * (COLUMN_WIDTH + COLUMN_GAP);
			int y = startY + row * (columnHeight + COLUMN_GAP);
			int[] saved = config.panels.get(column.category.id());
			boolean valid = saved != null && saved.length == 2;
			column.x = Math.clamp(valid ? saved[0] : x, 0, Math.max(0, width - COLUMN_WIDTH));
			column.y = Math.clamp(valid ? saved[1] : y, 0, Math.max(0, height - HEADER));
		}
	}

	private static List<String> options(Setting setting) {
		return switch (setting) {
			case ModeSetting m -> m.options;
			case ListSetting l -> l.options;
			default -> List.of();
		};
	}

	/** Option indices of a mode or list setting, wrapped into lines that fit in {@code width}. */
	private static List<List<Integer>> wrap(Draw d, List<String> options, int width) {
		List<List<Integer>> lines = new ArrayList<>();
		List<Integer> line = new ArrayList<>();
		int used = 0;
		int space = Ui.width(d, " ", SMALL) + 2;
		for (int i = 0; i < options.size(); i++) {
			int w = Ui.width(d, options.get(i), SMALL);
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

	private static final int BOOL_HEIGHT = 12;
	private static final int SLIDER_HEIGHT = 21;
	private static final int BIND_HEIGHT = 14;

	private static int settingHeight(Draw d, Setting setting, int innerWidth) {
		return switch (setting) {
			case BoolSetting b -> BOOL_HEIGHT;
			case SliderSetting s -> SLIDER_HEIGHT;
			case ModeSetting m -> optionBoxTop() + optionBoxHeight(d, m, innerWidth) + 4;
			case ListSetting l -> optionBoxTop() + optionBoxHeight(d, l, innerWidth) + 4;
		};
	}

	private static int optionBoxTop() {
		return 10;
	}

	private static int optionBoxHeight(Draw d, Setting setting, int innerWidth) {
		return wrap(d, options(setting), innerWidth - 10).size() * 9 + 6;
	}

	private static int settingsHeight(Draw d, Entry entry, int innerWidth) {
		if (!entry.expandable()) {
			return 0;
		}
		int h = 4;
		for (Setting setting : entry.settings) {
			if (setting.visible()) {
				h += settingHeight(d, setting, innerWidth);
			}
		}
		if (entry.module != null) {
			h += BIND_HEIGHT;
		}
		return h + 3;
	}

	// ---- rendering ----

	/** 0–1 open animation of column {@code index}, staggered left to right. */
	private float appear(int index) {
		if (Motion.reduced()) {
			return 1f;
		}
		float t = Math.clamp(((System.nanoTime() - openedAt) / 1_000_000f - index * 35) / 220f, 0f, 1f);
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	@Override
	public void render(Draw d, int mx, int my) {
		AmethystTheme t = AmethystTheme.current();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;

		AmethystHud.renderModules(d, true);
		d.layer();

		float fade = layoutFade.get();
		if (fade < 1f) {
			hits.clear();
			Entry wasHovered = hovered;
			hovered = null;
			if (layoutMode) {
				mx = my = -1;
			}
			float base = 1f - fade;
			d.alpha(appear(0) * base);
			d.fill(0, 0, width, height, 0x66000000);
			Ui.vGradient(d, 0, height - 60, width, 60, 0x00000000, 0x55000000);
			placeColumns();
			for (int i = 0; i < columns.size(); i++) {
				Column column = columns.get(i);
				column.shownScroll = Motion.reduced() || Math.abs(column.scroll - column.shownScroll) < 0.5
						? column.scroll
						: column.shownScroll + (column.scroll - column.shownScroll) * Math.min(1f, dt * 16);
				float p = appear(i);
				d.alpha(p * base);
				renderColumn(d, t, column, Math.round((1f - p) * -12), mx, my);
			}
			d.alpha(appear(columns.size()) * base);
			renderThemes(d, t, mx, my);
			renderSearch(d, t);
			renderFooter(d, t);
			if (hovered != wasHovered) {
				hoveredSince = now;
			}
			if (hovered != null && !hovered.description.isEmpty() && binding == null && draggingColumn == null
					&& draggingSlider == null && (now - hoveredSince) / 1_000_000 >= TOOLTIP_DELAY_MS) {
				d.layer();
				renderTooltip(d, t, hovered, mx, my);
			}
			d.alpha(1f);
		}
		if (fade > 0f) {
			renderLayoutOverlay(d, t, mx, my, fade);
		}
	}

	private void renderColumn(Draw d, AmethystTheme t, Column c, int slide, int mx, int my) {
		int x = c.x;
		int y = c.y + slide;
		c.collapse.set(config.collapsed.contains(c.category.id()) ? 1f : 0f);
		float folded = c.collapse.get();
		int bodyHeight = Math.round((columnHeight - HEADER) * (1f - folded));
		int bodyTop = y + HEADER;
		int bottom = bodyTop + bodyHeight;
		boolean searching = !query.isEmpty();

		// Shadow, then the header (icon + title) on a darker strip, then the body.
		Ui.shadow(d, x, y, COLUMN_WIDTH, HEADER + bodyHeight, RADIUS, 4, 0.35f);
		Ui.round(d, x, y, COLUMN_WIDTH, HEADER, RADIUS, AmethystTheme.withAlpha(t.bgSidebar(), 0.97f), true, bodyHeight < 2);
		if (bodyHeight >= 2) {
			Ui.round(d, x, bodyTop, COLUMN_WIDTH, bodyHeight, RADIUS, body(t), false, true);
		}
		String title = c.category.title;
		int titleW = Ui.width(d, title, TITLE);
		int iconX = x + (COLUMN_WIDTH - (9 + 6 + titleW)) / 2;
		Ui.icon(d, c.category.icon, iconX, y + (HEADER - 9) / 2, t.accent());
		Ui.text(d, title, iconX + 15, y + (HEADER - 9 * TITLE) / 2 + 1, AmethystTheme.TEXT_PRIMARY, TITLE);
		if (searching) {
			// How many of this column's modules match.
			String count = Integer.toString(c.matches);
			Ui.text(d, count, x + COLUMN_WIDTH - 8 - Ui.width(d, count, SMALL), y + (HEADER - 7) / 2f,
					c.matches > 0 ? t.accent() : AmethystTheme.TEXT_FAINT, SMALL);
		}
		hits.add(Hit.click(x, y, x + COLUMN_WIDTH, y + HEADER, () -> {
			draggingColumn = c;
			grabColumnX = (int) lastMouseX - c.x;
			grabColumnY = (int) lastMouseY - c.y;
		}, () -> toggleCollapsed(c), null));
		if (bodyHeight < 2) {
			return;
		}

		// Rows, scrolling inside the body.
		int clipTop = bodyTop + 1;
		int clipBottom = bottom - 3;
		boolean mouseInBody = mx >= x && mx < x + COLUMN_WIDTH && my >= clipTop && my < clipBottom;
		int rmx = mouseInBody ? mx : -1;
		int rmy = mouseInBody ? my : -1;
		int rowX = x + ROW_INSET;
		int rowW = COLUMN_WIDTH - ROW_INSET * 2;
		int innerW = rowW - SETTING_PAD * 2;

		d.clip(x, clipTop, x + COLUMN_WIDTH, clipBottom);
		int top = bodyTop + ROW_INSET - (int) Math.round(c.shownScroll);
		int rowY = top;
		c.matches = 0;
		for (Entry entry : c.entries) {
			if (!entry.matches(query)) {
				continue;
			}
			c.matches++;
			entry.expand.set(entry.expanded ? 1f : 0f);
			int h = ROW_HEIGHT + Math.round(settingsHeight(d, entry, innerW) * entry.expand.get());
			if (rowY + h >= clipTop && rowY < clipBottom) {
				renderRow(d, t, entry, rowX, rowY, rowW, h, innerW, rmx, rmy, clipTop, clipBottom);
			} else {
				entry.hover.set(0f);
			}
			rowY += h + ROW_GAP;
		}
		if (c.matches == 0) {
			Ui.centered(d, searching ? "No matches" : "Empty", x + COLUMN_WIDTH / 2, bodyTop + 14, AmethystTheme.TEXT_FAINT);
		}
		c.contentHeight = rowY - top + ROW_INSET - ROW_GAP;
		double max = maxScroll(c);
		c.scroll = Math.clamp(c.scroll, 0, max);

		// Rows fade out under the top and bottom edges when there's more to scroll to.
		int fadeColor = body(t);
		if (c.shownScroll > 1) {
			Ui.vGradient(d, x, clipTop, COLUMN_WIDTH, FADE, fadeColor, AmethystTheme.withAlpha(fadeColor, 0f));
		}
		if (c.shownScroll < max - 1) {
			Ui.vGradient(d, x, clipBottom - FADE, COLUMN_WIDTH, FADE, AmethystTheme.withAlpha(fadeColor, 0f), fadeColor);
		}
		d.unclip();

		// A thin scrollbar when the list doesn't fit.
		if (max > 0) {
			int track = clipBottom - clipTop - 8;
			int thumb = Math.max(14, (int) (track * track / (track + max)));
			int ty = clipTop + 4 + (int) ((track - thumb) * (c.shownScroll / max));
			Ui.round(d, x + COLUMN_WIDTH - 3, ty, 2, thumb, 1, AmethystTheme.withAlpha(t.accent(), 0.45f));
		}
	}

	private static int body(AmethystTheme t) {
		return AmethystTheme.withAlpha(AmethystTheme.lerp(t.bg(), t.bgCard(), 0.55f), 0.9f);
	}

	private void toggleCollapsed(Column c) {
		if (!config.collapsed.remove(c.category.id())) {
			config.collapsed.add(c.category.id());
		}
	}

	private double maxScroll(Column c) {
		return Math.max(0, c.contentHeight - (columnHeight - HEADER - 4));
	}

	private void renderRow(Draw d, AmethystTheme t, Entry entry, int x, int y, int w, int h, int innerW,
			int mx, int my, int clipTop, int clipBottom) {
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + ROW_HEIGHT;
		if (hover) {
			hovered = entry;
		}
		entry.hover.set(hover ? 1f : 0f);
		entry.active.set(entry.on.get() ? 1f : 0f);
		float hp = entry.hover.get();
		float ap = entry.active.get();

		int fill = AmethystTheme.lerp(AmethystTheme.withAlpha(t.bgSidebar(), 0.88f), AmethystTheme.withAlpha(t.bgCardHover(), 0.95f), hp);
		fill = AmethystTheme.lerp(fill, AmethystTheme.withAlpha(t.accent(), 0.9f), 0.06f * ap);
		Ui.round(d, x, y, w, h, ROW_RADIUS, fill);

		// Text is drawn after shapes, above the edge fades, so it fades itself near the edges.
		float center = y + ROW_HEIGHT / 2f;
		float textFade = Math.clamp((center - clipTop) / FADE, 0f, 1f) * Math.clamp((clipBottom - center) / FADE, 0f, 1f);

		int nameColor = AmethystTheme.lerp(AmethystTheme.lerp(AmethystTheme.TEXT_LABEL, AmethystTheme.TEXT_HOVER, hp), t.accent(), ap);
		String name = clipScaled(d, entry.name, w - 34, NAME);
		Ui.text(d, name, x + (w - Ui.width(d, name, NAME)) / 2f, y + (ROW_HEIGHT - 9 * NAME) / 2f + 1,
				AmethystTheme.withAlpha(nameColor, textFade), NAME);

		// The bound key, small on the left.
		if (entry.module != null && (entry.module.keybind() > 0 || binding == entry)) {
			String key = binding == entry ? "..." : keyName(entry.module.keybind());
			Ui.text(d, Ui.clip(d, key, 30), x + 5, y + (ROW_HEIGHT - 7) / 2f, AmethystTheme.withAlpha(binding == entry ? t.accent() : AmethystTheme.TEXT_FAINT, textFade), SMALL);
		}

		Runnable expand = entry.expandable() ? () -> entry.expanded = !entry.expanded : null;
		Runnable bind = entry.module != null ? () -> binding = binding == entry ? null : entry : null;
		addHit(Hit.click(x, y, x + w, y + ROW_HEIGHT, entry.click != null ? entry.click : expand, expand, bind), clipTop, clipBottom);
		if (expand != null) {
			// Green: settings open, red: closed. Its hit goes after the row's so it's found first.
			int dot = entry.expanded ? AmethystTheme.SUCCESS_TEXT : AmethystTheme.DANGER;
			Ui.round(d, x + w - 9, y + ROW_HEIGHT / 2 - 1, 3, 3, 1, AmethystTheme.withAlpha(dot, textFade));
			addHit(Hit.click(x + w - 14, y, x + w, y + ROW_HEIGHT, expand, expand, bind), clipTop, clipBottom);
		}

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
		d.fill(x + SETTING_PAD, y + ROW_HEIGHT, x + w - SETTING_PAD, y + ROW_HEIGHT + 1, AmethystTheme.withAlpha(AmethystTheme.WHITE, 0.05f));
		int ix = x + SETTING_PAD;
		int sy = y + ROW_HEIGHT + 4;
		for (Setting setting : entry.settings) {
			if (!setting.visible()) {
				continue;
			}
			if (sy >= clipEnd) {
				break;
			}
			switch (setting) {
				case BoolSetting b -> renderBool(d, b, ix, sy, innerW, mx, my, clipStart, clipEnd);
				case SliderSetting s -> renderSlider(d, t, s, ix, sy, innerW, mx, my, clipStart, clipEnd);
				case ModeSetting m -> renderOptions(d, t, m, ix, sy, innerW, mx, my, clipStart, clipEnd);
				case ListSetting l -> renderOptions(d, t, l, ix, sy, innerW, mx, my, clipStart, clipEnd);
			}
			sy += settingHeight(d, setting, innerW);
		}
		if (entry.module != null && sy < clipEnd) {
			renderBind(d, t, entry, ix, sy, innerW, mx, my, clipStart, clipEnd);
		}
		d.unclip();
	}

	private static String clipScaled(Draw d, String text, int maxWidth, float scale) {
		return Ui.clip(d, text, (int) (maxWidth / scale));
	}

	private void renderBool(Draw d, BoolSetting b, int x, int y, int w, int mx, int my, int clipTop, int clipBottom) {
		boolean on = b.get();
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + 10;
		Ui.icon(d, on ? Icons.CHECK : Icons.CROSS, x + 1, y + 1, on ? AmethystTheme.SUCCESS_TEXT : AmethystTheme.DANGER);
		Ui.text(d, b.name, x + 12, y + 1.5f, on || hover ? AmethystTheme.TEXT_PRIMARY : AmethystTheme.TEXT_LABEL, SMALL);
		addHit(Hit.click(x, y - 1, x + w, y + 10, b::toggle, null, null), clipTop, clipBottom);
	}

	private void renderSlider(Draw d, AmethystTheme t, SliderSetting s, int x, int y, int w, int mx, int my,
			int clipTop, int clipBottom) {
		Ui.text(d, s.name + ":", x, y, AmethystTheme.TEXT_PRIMARY, SMALL);
		String value = s.display();
		Ui.text(d, value, x + w - Ui.width(d, value, SMALL), y, AmethystTheme.TEXT_PRIMARY, SMALL);
		int by = y + 12;
		int filled = (int) Math.round(s.fraction() * w);
		Ui.round(d, x, by, w, 4, 2, AmethystTheme.withAlpha(AmethystTheme.WHITE, 0.08f));
		if (filled > 0) {
			// Gradient fill with a rounded (1px shorter) left end.
			d.fill(x, by + 1, x + 1, by + 3, t.accentPressed());
			Ui.hGradient(d, x + 1, by, filled - 1, 4, t.accentPressed(), t.accent());
		}
		boolean active = (draggingSlider != null && draggingSlider.slider() == s)
				|| (mx >= x - 3 && mx < x + w + 3 && my >= by - 4 && my < by + 8);
		int knob = active ? 7 : 5;
		int kx = Math.clamp(x + filled, x + knob / 2, x + w - knob / 2 - 1) - knob / 2;
		int ky = by + 2 - knob / 2;
		if (active) {
			Ui.round(d, kx - 2, ky - 2, knob + 4, knob + 4, (knob + 4) / 2, AmethystTheme.withAlpha(t.accent(), 0.25f));
		}
		Ui.round(d, kx, ky + 1, knob, knob, knob / 2, 0x50000000);
		Ui.round(d, kx, ky, knob, knob, knob / 2, AmethystTheme.WHITE);
		addHit(new Hit(x - 3, by - 4, x + w + 3, by + 8, null, null, null, s, x, w), clipTop, clipBottom);
	}

	/** A mode (one option) or list (any options) setting: the options in an inset box. */
	private void renderOptions(Draw d, AmethystTheme t, Setting setting, int x, int y, int w, int mx, int my,
			int clipTop, int clipBottom) {
		Ui.text(d, setting.name + ":", x, y, AmethystTheme.TEXT_PRIMARY, SMALL);
		List<String> options = options(setting);
		List<List<Integer>> lines = wrap(d, options, w - 10);
		int boxY = y + optionBoxTop();
		Ui.round(d, x, boxY, w, optionBoxHeight(d, setting, w), 3, AmethystTheme.withAlpha(t.bgSidebar(), 0.92f));
		int space = Ui.width(d, " ", SMALL) + 2;
		int ly = boxY + 4;
		for (List<Integer> line : lines) {
			int ox = x + 5;
			for (int i : line) {
				String option = options.get(i);
				int ow = Ui.width(d, option, SMALL);
				boolean hover = mx >= ox - 1 && mx < ox + ow + 1 && my >= ly - 1 && my < ly + 8;
				boolean selected = switch (setting) {
					case ModeSetting m -> option.equals(m.get());
					case ListSetting l -> l.has(option);
					default -> false;
				};
				int color = selected ? (hover ? t.accentHover() : t.accent()) : hover ? AmethystTheme.TEXT_HOVER : AmethystTheme.TEXT_LABEL;
				Ui.text(d, option, ox, ly, color, SMALL);
				Runnable pick = switch (setting) {
					case ModeSetting m -> () -> m.set(option);
					case ListSetting l -> () -> l.toggle(option);
					default -> null;
				};
				addHit(Hit.click(ox - 1, ly - 1, ox + ow + 1, ly + 8, pick, null, null), clipTop, clipBottom);
				ox += ow + space;
			}
			ly += 9;
		}
	}

	private void renderBind(Draw d, AmethystTheme t, Entry entry, int x, int y, int w, int mx, int my,
			int clipTop, int clipBottom) {
		boolean listening = binding == entry;
		Ui.text(d, "Bind:", x, y + 3, AmethystTheme.TEXT_PRIMARY, SMALL);
		int key = entry.module.keybind();
		String label = listening ? "Press a key..." : key > 0 ? keyName(key) : "None";
		int chipW = Ui.width(d, label, SMALL) + 10;
		int cx = x + w - chipW;
		boolean hover = mx >= cx && mx < x + w && my >= y && my < y + 11;
		int border = listening ? t.accent() : hover ? AmethystTheme.withAlpha(t.accent(), 0.5f) : t.border();
		Ui.box(d, cx, y, chipW, 11, 3, AmethystTheme.withAlpha(t.bgSidebar(), 0.92f), border);
		int color = listening ? t.accent() : key > 0 ? AmethystTheme.TEXT_PRIMARY : AmethystTheme.TEXT_MUTED;
		Ui.text(d, label, cx + 5, y + 2.5f, color, SMALL);
		Runnable start = () -> binding = listening ? null : entry;
		addHit(Hit.click(x, y, x + w, y + 11, start, () -> {
			entry.module.setKeybind(0);
			binding = null;
		}, start), clipTop, clipBottom);
	}

	/** Records a clickable area, cut to the visible part of the list. */
	private void addHit(Hit hit, int clipTop, int clipBottom) {
		int y1 = Math.max(hit.y1(), clipTop);
		int y2 = Math.min(hit.y2(), clipBottom);
		if (y2 > y1) {
			hits.add(new Hit(hit.x1(), y1, hit.x2(), y2, hit.left(), hit.right(), hit.middle(), hit.slider(), hit.trackX(), hit.trackW()));
		}
	}

	private void renderThemes(Draw d, AmethystTheme t, int mx, int my) {
		List<AmethystTheme> themes = AmethystTheme.ALL;
		int h = 26 + themes.size() * THEME_ROW + 6;
		int x = width - THEMES_WIDTH - 10;
		int y = height - h - 10;
		Ui.shadow(d, x, y, THEMES_WIDTH, h, RADIUS, 4, 0.35f);
		Ui.round(d, x, y, THEMES_WIDTH, h, RADIUS, AmethystTheme.withAlpha(t.bgSidebar(), 0.95f));
		Ui.round(d, x + 9, y + 11, 3, 3, 1, AmethystTheme.TEXT_PRIMARY);
		Ui.text(d, "Themes", x + 17, y + 8, AmethystTheme.TEXT_PRIMARY, 1.2f);
		int ry = y + 26;
		for (AmethystTheme theme : themes) {
			boolean selected = theme == t;
			boolean hover = mx >= x && mx < x + THEMES_WIDTH && my >= ry && my < ry + THEME_ROW;
			if (hover) {
				Ui.round(d, x + 4, ry, THEMES_WIDTH - 8, THEME_ROW, 3, AmethystTheme.withAlpha(AmethystTheme.WHITE, 0.04f));
			}
			Ui.round(d, x + 10, ry + 3, 8, 8, 2, theme.accent());
			Ui.round(d, x + 20, ry + 3, 8, 8, 2, theme.accentPressed());
			int color = selected ? AmethystTheme.TEXT_HEADLINE : hover ? AmethystTheme.TEXT_HOVER : AmethystTheme.TEXT_LABEL;
			d.text(theme.name(), x + 33, ry + 4, color);
			if (selected) {
				Ui.icon(d, Icons.CHECK, x + THEMES_WIDTH - 15, ry + 4, theme.accent());
			}
			hits.add(Hit.click(x, ry, x + THEMES_WIDTH, ry + THEME_ROW, () -> config.theme = theme.name(), null, null));
			ry += THEME_ROW;
		}
	}

	/** The search pill at the top; typing anywhere fills it. */
	private void renderSearch(Draw d, AmethystTheme t) {
		int x = (width - SEARCH_WIDTH) / 2;
		int y = 7;
		boolean active = !query.isEmpty();
		Ui.box(d, x, y, SEARCH_WIDTH, SEARCH_HEIGHT, SEARCH_HEIGHT / 2, AmethystTheme.withAlpha(t.bgSidebar(), 0.95f),
				active ? AmethystTheme.withAlpha(t.accent(), 0.6f) : AmethystTheme.withAlpha(t.border(), 0.9f));
		Ui.icon(d, Icons.SEARCH, x + 9, y + (SEARCH_HEIGHT - 7) / 2, active ? t.accent() : AmethystTheme.TEXT_MUTED);
		int tx = x + 21;
		int ty = y + (SEARCH_HEIGHT - d.lineHeight()) / 2 + 1;
		if (active) {
			String shown = query;
			while (d.textWidth(shown) > SEARCH_WIDTH - 32 && shown.length() > 1) {
				shown = shown.substring(1);
			}
			d.text(shown, tx, ty, AmethystTheme.TEXT_PRIMARY);
			if ((System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = tx + d.textWidth(shown) + 1;
				d.fill(cx, ty - 1, cx + 1, ty + 8, t.accent());
			}
		} else {
			d.text("Type to search modules", tx, ty, AmethystTheme.TEXT_FAINT);
		}
	}

	private void renderFooter(Draw d, AmethystTheme t) {
		String hint = binding != null
				? "Press a key to bind " + binding.name + "  ·  Backspace clears  ·  Esc cancels"
				: "Click: toggle  ·  Right click: settings  ·  Middle click: bind";
		int y = height - 13;
		Ui.centered(d, hint, width / 2, y, binding != null ? t.accent() : AmethystTheme.TEXT_FAINT);
		int w = d.textWidth("Amethyst Client");
		Ui.wordmark(d, t, (width - w) / 2, y - 11);
	}

	private void renderTooltip(Draw d, AmethystTheme t, Entry entry, int mx, int my) {
		List<String> lines = wrapText(d, entry.description, TOOLTIP_WIDTH - 12, SMALL);
		int w = 12;
		for (String line : lines) {
			w = Math.max(w, Ui.width(d, line, SMALL) + 12);
		}
		w = Math.max(w, Ui.width(d, entry.name, 1f) + 12);
		int h = 8 + 10 + lines.size() * 8 + 4;
		int x = Math.min(mx + 10, width - w - 4);
		int y = Math.min(my + 12, height - h - 4);
		Ui.shadow(d, x, y, w, h, 4, 3, 0.4f);
		Ui.box(d, x, y, w, h, 4, AmethystTheme.withAlpha(t.bgSidebar(), 0.98f), AmethystTheme.withAlpha(t.accent(), 0.45f));
		d.text(entry.name, x + 6, y + 6, t.accent());
		int ly = y + 17;
		for (String line : lines) {
			Ui.text(d, line, x + 6, ly, AmethystTheme.TEXT_SECONDARY, SMALL);
			ly += 8;
		}
	}

	/** {@code text} split on spaces into lines no wider than {@code maxWidth} at {@code scale}. */
	private static List<String> wrapText(Draw d, String text, int maxWidth, float scale) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && Ui.width(d, next, scale) > maxWidth) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	/** Short display name of a GLFW key. */
	static String keyName(int key) {
		String name = key == GLFW.GLFW_KEY_SPACE ? null : GLFW.glfwGetKeyName(key, 0);
		if (name != null && !name.isBlank()) {
			return name.toUpperCase(Locale.ROOT);
		}
		return switch (key) {
			case GLFW.GLFW_KEY_SPACE -> "Space";
			case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
			case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
			case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
			case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
			case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
			case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
			case GLFW.GLFW_KEY_TAB -> "Tab";
			case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps";
			case GLFW.GLFW_KEY_ENTER -> "Enter";
			case GLFW.GLFW_KEY_UP -> "Up";
			case GLFW.GLFW_KEY_DOWN -> "Down";
			case GLFW.GLFW_KEY_LEFT -> "Left";
			case GLFW.GLFW_KEY_RIGHT -> "Right";
			case GLFW.GLFW_KEY_INSERT -> "Ins";
			case GLFW.GLFW_KEY_HOME -> "Home";
			case GLFW.GLFW_KEY_END -> "End";
			case GLFW.GLFW_KEY_PAGE_UP -> "PgUp";
			case GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn";
			default -> key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25 ? "F" + (key - GLFW.GLFW_KEY_F1 + 1)
					: key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9 ? "Num" + (key - GLFW.GLFW_KEY_KP_0)
					: "Key " + key;
		};
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

		HudModule hoveredModule = layoutMode && draggingModule == null ? moduleAt(mx, my) : null;
		for (HudModule module : Modules.HUD) {
			if (!module.enabled() || module.width == 0) {
				continue;
			}
			int x = Math.round(module.x(hudW) * s) - 2;
			int y = Math.round(module.y(hudH) * s) - 2;
			int w = Math.round(module.width * s) + 4;
			int h = Math.round(module.height * s) + 4;
			int color = module == draggingModule ? t.accent()
					: module == hoveredModule ? AmethystTheme.withAlpha(t.accent(), 0.5f)
					: AmethystTheme.withAlpha(AmethystTheme.WHITE, 0.25f);
			Ui.outline(d, x, y, w, h, Ui.RADIUS_CARD, color);
			if (module == hoveredModule || module == draggingModule) {
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
		for (int i = Modules.HUD.size() - 1; i >= 0; i--) {
			HudModule module = Modules.HUD.get(i);
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
			} else {
				Runnable action = switch (button) {
					case GLFW.GLFW_MOUSE_BUTTON_LEFT -> hit.left();
					case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> hit.right();
					case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> hit.middle();
					default -> null;
				};
				if (action != null) {
					action.run();
				}
			}
			return true;
		}
		// A click on empty space stops waiting for a key.
		binding = null;
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
			config.panels.put(draggingColumn.category.id(), new int[] {x, y});
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
		for (int i = columns.size() - 1; i >= 0; i--) {
			Column c = columns.get(i);
			if (mx >= c.x && mx < c.x + COLUMN_WIDTH && my >= c.y && my < c.y + columnHeight) {
				c.scroll = Math.clamp(c.scroll - amount * 18, 0, maxScroll(c));
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(int key) {
		if (binding != null) {
			if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
				binding.module.setKeybind(0);
			} else if (key != GLFW.GLFW_KEY_ESCAPE) {
				binding.module.setKeybind(key);
			}
			binding = null;
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (layoutMode) {
				setLayoutMode(false);
				return true;
			}
			if (!query.isEmpty()) {
				setQuery("");
				return true;
			}
			return false;
		}
		if (layoutMode) {
			return false;
		}
		if (key == GLFW.GLFW_KEY_BACKSPACE) {
			if (!query.isEmpty()) {
				setQuery(query.substring(0, query.length() - 1));
			}
			return true;
		}
		String typed = key == GLFW.GLFW_KEY_SPACE ? " " : GLFW.glfwGetKeyName(key, 0);
		if (typed != null && typed.length() == 1 && query.length() < 32 && !(query.isEmpty() && typed.equals(" "))) {
			setQuery(query + typed.toLowerCase(Locale.ROOT));
			return true;
		}
		return false;
	}

	private void setQuery(String query) {
		this.query = query;
		for (Column column : columns) {
			column.scroll = 0;
		}
	}
}
