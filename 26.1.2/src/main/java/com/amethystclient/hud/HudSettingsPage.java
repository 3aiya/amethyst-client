package com.amethystclient.hud;

import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.hud.setting.ListSetting;
import com.amethystclient.hud.setting.ModeSetting;
import com.amethystclient.hud.setting.Setting;
import com.amethystclient.hud.setting.SliderSetting;
import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Glyphs;
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
 * The ClickGUI, opened with Right Shift: one fixed column per {@link Category}, centred on screen,
 * each with an icon header and a scrolling list of modules. Left click turns a module on/off (its
 * name turns accent), right click (or the dot) opens its settings: sliders, option lists,
 * check/cross switches and the keybind. Middle click binds a key straight away. Typing searches
 * every column. "Edit Layout" switches to a mode where the HUD modules can be dragged around.
 *
 * <p>The columns are drawn in screen pixels, not GUI pixels, so they're sharp at every GUI scale.
 * Sizes below are for a 1080p screen and scale with the screen height.
 */
public class HudSettingsPage extends Page {
	// ---- sizes, in pixels on a 1080p screen ----
	private static final int REFERENCE_HEIGHT = 1080;
	private static final int COLUMN_WIDTH = 284;
	private static final int COLUMN_HEIGHT = 525;
	private static final int COLUMN_GAP = 21;
	private static final int HEADER = 60;
	private static final int RADIUS = 12;
	private static final int ROW_HEIGHT = 40;
	private static final int ROW_GAP = 12;
	private static final int ROW_INSET = 13;
	private static final int ROW_RADIUS = 9;
	/** Left/right padding of a row's settings. */
	private static final int PAD = 12;
	/** Where the settings start, from the top of the row. */
	private static final int SETTINGS_TOP = 34;
	private static final int SETTINGS_BOTTOM = 6;
	private static final int SLIDER_BLOCK = 33;
	private static final int SWITCH_BLOCK = 27;
	private static final int OPTION_LINE = 19;
	private static final int OPTION_GAP = 8;
	private static final int DOT = 5;
	private static final int FADE = 30;
	// Fonts (assets/amethystclient/font): Lexend for headings and module names, Inter for labels.
	private static final String HEADING = "lexend_medium";
	private static final String MODULE = "lexend";
	private static final String LABEL = "inter";
	private static final String VALUE = "inter_medium";
	// Text sizes (font scale at 1080p).
	private static final float TITLE = 2.48f;
	private static final float NAME = 2.1f;
	private static final float SMALL = 1.3f;
	private static final float TOOLTIP = 1.4f;

	// ---- colours that aren't theme colours ----
	private static final int NAME_OFF = 0xFF7A7682;
	private static final int NAME_HOVER = 0xFFB4AFBC;
	private static final int TITLE_COLOR = 0xFFB0AAB6;
	/** Setting labels of an enabled module, and of a disabled one. */
	private static final int TEXT = 0xFFF7F5FA;
	private static final int TEXT_OFF = 0xFF77737E;
	private static final int OPTION = 0xFF78747C;
	private static final int TRACK = 0xFF120D15;
	private static final int KNOB_OFF = 0xFF77717A;
	private static final int GREEN = 0xFF6CFF72;
	private static final int RED = 0xFFFF4E57;
	private static final int CHECK = 0xFF67FF75;
	private static final int CROSS = 0xFFFA4A4D;

	private static final long TOOLTIP_DELAY_MS = 600;
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
		final Motion expand = new Motion(0f, 200);
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

		boolean expandable() {
			return module != null || !settings.isEmpty();
		}

		/** How "on" the settings look: an off module's accents are muted. Actions are always on. */
		float settingsOn() {
			return module == null ? 1f : active.get();
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
		/** Where the list is scrolling to, and where it's drawn (eases towards {@link #scroll}). */
		double scroll;
		double shownScroll;
		int contentHeight;
		int x;
		int y;

		Column(Category category, List<Entry> entries) {
			this.category = category;
			this.entries = entries;
		}
	}

	/** A clickable area from the last frame, in screen pixels. */
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

	/** Screen pixels per GUI pixel, and the design's scale, from the last frame. */
	private float guiScale = 1f;
	private float k = 1f;
	private long lastFrame = System.nanoTime();

	private boolean layoutMode;
	private String query = "";
	/** The module waiting for a key, or null. */
	private Entry binding;
	private Entry hovered;
	private long hoveredSince;

	// Dragging: a slider, or (layout mode) a HUD module.
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
			entries.addAll(actions(category));
			if (!entries.isEmpty()) {
				columns.add(new Column(category, entries));
			}
		}
		layoutDone = new FlatButton("Done", FlatButton.Variant.PRIMARY, () -> setLayoutMode(false));
	}

	/** The client's own options, listed after a category's modules. */
	private List<Entry> actions(Category category) {
		return switch (category) {
			case RENDER -> List.of(new Entry("HUD Style", "Theme, background and size of the HUD and this menu", null,
					() -> false, null, List.of(
							new ModeSetting("Theme", AmethystTheme.ALL.stream().map(AmethystTheme::name).toList(),
									() -> AmethystTheme.current().name(), v -> config.theme = v),
							new SliderSetting("Background", 0, 1, 0.05, () -> config.backgroundOpacity,
									v -> config.backgroundOpacity = v, v -> Math.round(v * 100) + "%"),
							new SliderSetting("Scale", 0.5, 2, 0.05, () -> config.scale,
									v -> config.scale = v, v -> Math.round(v * 100) + "%"))));
			case MISC -> List.of(
					new Entry("Edit Layout", "Drag the HUD modules around the screen", null,
							() -> false, () -> setLayoutMode(true), List.of()),
					new Entry("Reduce Motion", "Turns off the animations", null,
							() -> config.reduceMotion, () -> config.reduceMotion = !config.reduceMotion, List.of()),
					new Entry("Reset Layout", "Puts every HUD module back where it started", null,
							() -> false, this::resetLayout, List.of()));
			default -> List.of();
		};
	}

	private void setLayoutMode(boolean on) {
		layoutMode = on;
		layoutFade.set(on ? 1f : 0f);
		draggingModule = null;
		draggingSlider = null;
		binding = null;
	}

	private void resetLayout() {
		for (HudModule module : Modules.HUD) {
			module.resetPosition();
		}
	}

	@Override
	public void removed() {
		config.save();
	}

	// ---- sizes ----

	/** A design size in screen pixels. */
	private int px(double value) {
		return (int) Math.round(value * k);
	}

	private int screenWidth() {
		return Math.round(width * guiScale);
	}

	private int screenHeight() {
		return Math.round(height * guiScale);
	}

	/** Fits the design to the screen: 1 at 1080p, smaller when the columns wouldn't fit. */
	private void measure(Draw d) {
		guiScale = d.guiScale();
		int n = Math.max(1, columns.size());
		float total = n * COLUMN_WIDTH + (n - 1) * COLUMN_GAP;
		k = Math.min((float) screenHeight() / REFERENCE_HEIGHT,
				Math.min((screenWidth() - 40) / total, (screenHeight() - 110) / (float) COLUMN_HEIGHT));
		k = Math.max(0.35f, k);
		int columnWidth = px(COLUMN_WIDTH);
		int gap = px(COLUMN_GAP);
		int x = (screenWidth() - (n * columnWidth + (n - 1) * gap)) / 2;
		int y = (screenHeight() - px(COLUMN_HEIGHT)) / 2;
		for (Column column : columns) {
			column.x = x;
			column.y = y;
			x += columnWidth + gap;
		}
	}

	/** Icons are drawn at a whole multiple of their texture size, so their pixels stay sharp. */
	private int iconSize(String glyph) {
		return Glyphs.cell(glyph) * Math.max(1, Math.round(k));
	}

	private float font(float scale) {
		return scale * k;
	}

	/** Text in {@code family} with its middle (of the capitals) at {@code centerY}. */
	private static void text(Draw d, String family, String text, float x, float centerY, int color, float scale) {
		Ui.sharpText(d, family, text, Math.round(x), Math.round(centerY - 3.95f * scale), color, scale);
	}

	private static int width(Draw d, String family, String text, float scale) {
		return Ui.sharpWidth(d, family, text, scale);
	}

	/** {@code argb} with its colour channels multiplied by {@code factor} (alpha kept). */
	private static int shade(int argb, float factor) {
		int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
		int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
		int b = Math.min(255, Math.round((argb & 0xFF) * factor));
		return (argb & 0xFF000000) | r << 16 | g << 8 | b;
	}

	// ---- option lists ----

	private static List<String> options(Setting setting) {
		return switch (setting) {
			case ModeSetting m -> m.options;
			case ListSetting l -> l.options;
			default -> List.of();
		};
	}

	/** Option indices wrapped into lines that fit in {@code width}. */
	private List<List<Integer>> wrap(Draw d, List<String> options, int width) {
		List<List<Integer>> lines = new ArrayList<>();
		List<Integer> line = new ArrayList<>();
		int used = 0;
		int gap = px(OPTION_GAP);
		for (int i = 0; i < options.size(); i++) {
			int w = width(d, LABEL, options.get(i), font(SMALL));
			if (!line.isEmpty() && used + gap + w > width) {
				lines.add(line);
				line = new ArrayList<>();
				used = 0;
			}
			used += (line.isEmpty() ? 0 : gap) + w;
			line.add(i);
		}
		if (!line.isEmpty()) {
			lines.add(line);
		}
		return lines;
	}

	/** Height of the inset box of options, for settings {@code width} wide. */
	private int optionBoxHeight(Draw d, Setting setting, int width) {
		return wrap(d, options(setting), width - px(PAD) * 2).size() * px(OPTION_LINE) + px(10);
	}

	/** Height of one setting, for settings {@code width} wide. */
	private int blockHeight(Draw d, Setting setting, int width) {
		return switch (setting) {
			case BoolSetting b -> px(SWITCH_BLOCK);
			case SliderSetting s -> px(SLIDER_BLOCK);
			case ModeSetting m -> px(20) + optionBoxHeight(d, m, width) + px(4);
			case ListSetting l -> px(20) + optionBoxHeight(d, l, width) + px(4);
		};
	}

	/** Extra height of an entry's row when its settings are open, for settings {@code width} wide. */
	private int settingsHeight(Draw d, Entry entry, int width) {
		if (!entry.expandable()) {
			return 0;
		}
		int h = px(SETTINGS_TOP) - px(ROW_HEIGHT);
		for (Setting setting : entry.settings) {
			if (setting.visible()) {
				h += blockHeight(d, setting, width);
			}
		}
		if (entry.module != null) {
			h += px(SWITCH_BLOCK);
		}
		return h + px(SETTINGS_BOTTOM);
	}

	// ---- colours ----

	private static int header(AmethystTheme t) {
		return AmethystTheme.withAlpha(AmethystTheme.lerp(t.bgSidebar(), t.accent(), 0.02f), 0.97f);
	}

	private static int body(AmethystTheme t) {
		return AmethystTheme.withAlpha(t.bgCard(), 0.94f);
	}

	/** The accent, a bit brighter: enabled module names and the column icons. */
	private static int bright(AmethystTheme t) {
		return shade(t.accent(), 1.1f);
	}

	private static int row(AmethystTheme t, float hover) {
		return AmethystTheme.lerp(
				AmethystTheme.withAlpha(AmethystTheme.lerp(t.bgSidebar(), t.bgCard(), 0.5f), 0.9f),
				AmethystTheme.withAlpha(t.bgCardHover(), 0.95f), hover);
	}

	private static int inset(AmethystTheme t) {
		return AmethystTheme.withAlpha(shade(t.bgSidebar(), 0.85f), 0.95f);
	}

	// ---- rendering ----

	/** 0–1 open animation of column {@code index}, staggered left to right. */
	private float appear(int index) {
		if (Motion.reduced()) {
			return 1f;
		}
		float t = Math.clamp(((System.nanoTime() - openedAt) / 1_000_000f - index * 40) / 260f, 0f, 1f);
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
			float base = 1f - fade;
			d.alpha(appear(0) * base);
			d.fill(0, 0, width, height, 0x40000000);

			// Everything else in screen pixels.
			measure(d);
			d.push();
			d.scale(1f / guiScale);
			int smx = layoutMode ? -1 : Math.round(mx * guiScale);
			int smy = layoutMode ? -1 : Math.round(my * guiScale);
			for (int i = 0; i < columns.size(); i++) {
				Column column = columns.get(i);
				column.shownScroll = Motion.reduced() || Math.abs(column.scroll - column.shownScroll) < 0.5
						? column.scroll
						: column.shownScroll + (column.scroll - column.shownScroll) * Math.min(1f, dt * 16);
				float p = appear(i);
				d.alpha(p * base);
				renderColumn(d, t, column, Math.round((1f - p) * px(16)), smx, smy);
			}
			d.alpha(appear(0) * base);
			renderTopBar(d, t);
			if (hovered != wasHovered) {
				hoveredSince = now;
			}
			if (hovered != null && binding == null && draggingSlider == null
					&& (now - hoveredSince) / 1_000_000 >= TOOLTIP_DELAY_MS) {
				d.layer();
				renderTooltip(d, t, hovered, smx, smy);
			}
			d.pop();
			d.alpha(1f);
		}
		if (fade > 0f) {
			renderLayoutOverlay(d, t, mx, my, fade);
		}
	}

	private void renderColumn(Draw d, AmethystTheme t, Column c, int slide, int mx, int my) {
		int x = c.x;
		int y = c.y + slide;
		int w = px(COLUMN_WIDTH);
		int h = px(COLUMN_HEIGHT);
		int header = px(HEADER);
		int radius = px(RADIUS);
		int bodyTop = y + header;
		int bottom = y + h;

		Ui.smoothShadow(d, x, y, w, h, radius, px(10), 0.32f);
		Ui.smooth(d, x, bodyTop, w, h - header, radius, body(t), false, true);

		// Rows, scrolling under the header.
		int clipTop = bodyTop;
		int clipBottom = bottom - px(3);
		boolean mouseInBody = mx >= x && mx < x + w && my >= clipTop && my < clipBottom;
		int rmx = mouseInBody ? mx : -1;
		int rmy = mouseInBody ? my : -1;
		int rowX = x + px(ROW_INSET);
		int rowW = w - px(ROW_INSET) * 2;

		d.clip(x, clipTop, x + w, clipBottom);
		int top = bodyTop + px(ROW_INSET) - (int) Math.round(c.shownScroll);
		int rowY = top;
		int shown = 0;
		for (Entry entry : c.entries) {
			if (!entry.matches(query)) {
				continue;
			}
			shown++;
			entry.expand.set(entry.expanded ? 1f : 0f);
			float open = entry.expand.get();
			int rowH = px(ROW_HEIGHT) + (open > 0f ? Math.round(settingsHeight(d, entry, rowW - px(PAD) * 2) * open) : 0);
			if (rowY + rowH >= clipTop && rowY < clipBottom) {
				renderRow(d, t, entry, rowX, rowY, rowW, rowH, rmx, rmy, clipTop, clipBottom);
			} else {
				entry.hover.set(0f);
			}
			rowY += rowH + px(ROW_GAP);
		}
		if (shown == 0) {
			text(d, LABEL, "No matches", x + (w - width(d, LABEL, "No matches", font(SMALL))) / 2f, bodyTop + px(30),
					AmethystTheme.TEXT_FAINT, font(SMALL));
		}
		c.contentHeight = rowY - top + px(ROW_INSET) - px(ROW_GAP);
		double max = maxScroll(c);
		c.scroll = Math.clamp(c.scroll, 0, max);

		// Rows fade into the body under the header and at the bottom when there's more to scroll to.
		int fadeH = px(FADE);
		int fadeColor = body(t);
		if (c.shownScroll > 1) {
			Ui.vGradient(d, x, clipTop, w, fadeH, fadeColor, AmethystTheme.withAlpha(fadeColor, 0f));
		}
		if (c.shownScroll < max - 1) {
			Ui.vGradient(d, x, clipBottom - fadeH, w, fadeH, AmethystTheme.withAlpha(fadeColor, 0f), fadeColor);
		}
		d.unclip();

		// The header goes on top, so a row scrolling under it is hidden.
		Ui.smooth(d, x, y, w, header, radius, header(t), true, false);
		String title = c.category.title;
		int icon = iconSize(c.category.icon);
		float titleScale = font(TITLE);
		int titleW = width(d, HEADING, title, titleScale);
		int gap = px(10);
		int left = x + (w - (icon + gap + titleW)) / 2;
		Glyphs.draw(d, c.category.icon, left, y + (header - icon) / 2, icon, bright(t));
		text(d, HEADING, title, left + icon + gap, y + header / 2f, TITLE_COLOR, titleScale);
	}

	private double maxScroll(Column c) {
		return Math.max(0, c.contentHeight - (px(COLUMN_HEIGHT) - px(HEADER) - px(3)));
	}

	private void renderRow(Draw d, AmethystTheme t, Entry entry, int x, int y, int w, int h,
			int mx, int my, int clipTop, int clipBottom) {
		int rowH = px(ROW_HEIGHT);
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + rowH;
		if (hover) {
			hovered = entry;
		}
		entry.hover.set(hover ? 1f : 0f);
		entry.active.set(entry.on.get() ? 1f : 0f);
		float hp = entry.hover.get();
		float ap = entry.active.get();

		Ui.smooth(d, x, y, w, h, px(ROW_RADIUS), row(t, hp));

		// Text is drawn after shapes, so it fades itself near the edges instead of under the fades.
		float center = y + rowH / 2f;
		float edge = px(FADE) * 0.8f;
		float textFade = Math.clamp((center - clipTop) / edge, 0f, 1f) * Math.clamp((clipBottom - center) / edge, 0f, 1f);

		float nameScale = font(NAME);
		int nameColor = AmethystTheme.lerp(AmethystTheme.lerp(NAME_OFF, NAME_HOVER, hp), bright(t), ap);
		String name = Ui.sharpClip(d, MODULE, entry.name, w - px(60), nameScale);
		text(d, MODULE, name, x + (w - width(d, MODULE, name, nameScale)) / 2f, center, AmethystTheme.withAlpha(nameColor, textFade), nameScale);

		// The bound key, small on the left.
		if (entry.module != null && (entry.module.keybind() > 0 || binding == entry)) {
			String key = binding == entry ? "..." : keyName(entry.module.keybind());
			int color = binding == entry ? t.accent() : AmethystTheme.TEXT_FAINT;
			text(d, LABEL, Ui.sharpClip(d, LABEL, key, px(40), font(SMALL)), x + px(PAD), center,
					AmethystTheme.withAlpha(color, textFade), font(SMALL));
		}

		Runnable expand = entry.expandable() ? () -> entry.expanded = !entry.expanded : null;
		Runnable bind = entry.module != null ? () -> binding = binding == entry ? null : entry : null;
		addHit(Hit.click(x, y, x + w, y + rowH, entry.click != null ? entry.click : expand, expand, bind), clipTop, clipBottom);
		if (expand != null) {
			// Green: settings open, red: closed. Its hit goes after the row's so it's found first.
			int dot = px(DOT);
			int dx = x + w - px(16) - dot / 2;
			Ui.circle(d, dx, Math.round(center) - dot / 2, dot, AmethystTheme.withAlpha(entry.expanded ? GREEN : RED, textFade));
			addHit(Hit.click(dx - px(10), y, x + w, y + rowH, expand, expand, bind), clipTop, clipBottom);
		}

		if (h <= rowH) {
			return;
		}
		// Settings, clipped to the row while it opens or closes.
		int clipStart = Math.max(y + rowH - px(6), clipTop);
		int clipEnd = Math.min(y + h, clipBottom);
		if (clipEnd <= clipStart) {
			return;
		}
		d.clip(x, clipStart, x + w, clipEnd);
		int ix = x + px(PAD);
		int iw = w - px(PAD) * 2;
		int sy = y + px(SETTINGS_TOP);
		float on = entry.settingsOn();
		for (Setting setting : entry.settings) {
			if (!setting.visible()) {
				continue;
			}
			if (sy >= clipEnd) {
				break;
			}
			switch (setting) {
				case BoolSetting b -> renderBool(d, b, ix, sy, iw, on, mx, my, clipStart, clipEnd);
				case SliderSetting s -> renderSlider(d, t, s, ix, sy, iw, on, mx, my, clipStart, clipEnd);
				case ModeSetting m -> renderOptions(d, t, m, ix, sy, iw, on, mx, my, clipStart, clipEnd);
				case ListSetting l -> renderOptions(d, t, l, ix, sy, iw, on, mx, my, clipStart, clipEnd);
			}
			sy += blockHeight(d, setting, iw);
		}
		if (entry.module != null && sy < clipEnd) {
			renderBind(d, t, entry, ix, sy, iw, mx, my, clipStart, clipEnd);
		}
		d.unclip();
	}

	/** Label colour of a setting; {@code on} is how enabled its module is. */
	private static int label(float on) {
		return AmethystTheme.lerp(TEXT_OFF, TEXT, on);
	}

	private void renderBool(Draw d, BoolSetting b, int x, int y, int w, float on, int mx, int my, int clipTop, int clipBottom) {
		boolean checked = b.get();
		int block = px(SWITCH_BLOCK);
		float cy = y + block / 2f;
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + block;
		String glyph = checked ? Glyphs.CHECK : Glyphs.CROSS;
		int icon = iconSize(glyph);
		Glyphs.draw(d, glyph, x, Math.round(cy - icon / 2f), icon, shade(checked ? CHECK : CROSS, 0.52f + 0.48f * on));
		text(d, LABEL, b.name, x + icon + px(7), cy, hover ? AmethystTheme.lerp(label(on), AmethystTheme.WHITE, 0.5f) : label(on), font(SMALL));
		addHit(Hit.click(x, y, x + w, y + block, b::toggle, null, null), clipTop, clipBottom);
	}

	private void renderSlider(Draw d, AmethystTheme t, SliderSetting s, int x, int y, int w, float on, int mx, int my,
			int clipTop, int clipBottom) {
		float labelY = y + px(8);
		text(d, LABEL, s.name + ":", x, labelY, label(on), font(SMALL));
		String value = s.display();
		text(d, VALUE, value, x + w - width(d, VALUE, value, font(SMALL)), labelY, label(on), font(SMALL));

		int barH = Math.max(2, px(4));
		int barY = y + px(21) - barH / 2;
		int filled = (int) Math.round(s.fraction() * w);
		Ui.smooth(d, x, barY, w, barH, barH / 2, TRACK);
		if (filled > 0) {
			// Bright at the start, a little darker at the knob; a dim purple when the module is off.
			int dim = shade(t.accent(), 0.17f);
			int from = AmethystTheme.lerp(dim, shade(t.accent(), 1.06f), on);
			int to = AmethystTheme.lerp(dim, shade(t.accent(), 0.84f), on);
			// Rounded left end, then the gradient.
			Ui.smooth(d, x, barY, Math.min(filled, barH), barH, barH / 2, from);
			if (filled > barH / 2) {
				Ui.hGradient(d, x + barH / 2, barY, filled - barH / 2, barH, from, to);
			}
		}
		boolean dragging = draggingSlider != null && draggingSlider.slider() == s;
		boolean hover = mx >= x - px(4) && mx < x + w + px(4) && my >= barY - px(8) && my < barY + barH + px(8);
		int knob = px(dragging || hover ? 12 : 10);
		int kx = Math.clamp(x + filled - knob / 2, x - knob / 2, x + w - knob / 2);
		int ky = barY + barH / 2 - knob / 2;
		if (dragging || hover) {
			int glow = knob + px(8);
			Ui.circle(d, kx - px(4), ky - px(4), glow, AmethystTheme.withAlpha(t.accent(), 0.22f * on));
		}
		Ui.circle(d, kx, ky + Math.max(1, px(1)), knob, 0x55000000);
		Ui.circle(d, kx, ky, knob, AmethystTheme.lerp(KNOB_OFF, AmethystTheme.WHITE, on));
		addHit(new Hit(x - px(6), barY - px(9), x + w + px(6), barY + barH + px(9), null, null, null, s, x, w),
				clipTop, clipBottom);
	}

	/** A mode (one option) or list (any options) setting: the options in an inset box. */
	private void renderOptions(Draw d, AmethystTheme t, Setting setting, int x, int y, int w, float on, int mx, int my,
			int clipTop, int clipBottom) {
		float small = font(SMALL);
		text(d, LABEL, setting.name + ":", x, y + px(8), label(on), small);
		List<String> options = options(setting);
		int boxY = y + px(20);
		int boxH = optionBoxHeight(d, setting, w);
		Ui.smooth(d, x, boxY, w, boxH, px(7), inset(t));
		int gap = px(OPTION_GAP);
		int line = px(OPTION_LINE);
		float ly = boxY + px(5) + line / 2f;
		int selectedColor = shade(t.accent(), 0.4f + 0.45f * on);
		for (List<Integer> row : wrap(d, options, w - px(PAD) * 2)) {
			int ox = x + px(PAD);
			for (int i : row) {
				String option = options.get(i);
				int ow = width(d, LABEL, option, small);
				int top = Math.round(ly - line / 2f);
				boolean hover = mx >= ox - 2 && mx < ox + ow + 2 && my >= top && my < top + line;
				boolean selected = switch (setting) {
					case ModeSetting m -> option.equals(m.get());
					case ListSetting l -> l.has(option);
					default -> false;
				};
				int color = selected
						? (hover ? AmethystTheme.lerp(selectedColor, AmethystTheme.WHITE, 0.25f) : selectedColor)
						: hover ? label(on) : OPTION;
				text(d, LABEL, option, ox, ly, color, small);
				Runnable pick = switch (setting) {
					case ModeSetting m -> () -> m.set(option);
					case ListSetting l -> () -> l.toggle(option);
					default -> null;
				};
				addHit(Hit.click(ox - 2, top, ox + ow + 2, top + line, pick, null, null), clipTop, clipBottom);
				ox += ow + gap;
			}
			ly += line;
		}
	}

	private void renderBind(Draw d, AmethystTheme t, Entry entry, int x, int y, int w, int mx, int my,
			int clipTop, int clipBottom) {
		boolean listening = binding == entry;
		int block = px(SWITCH_BLOCK);
		float cy = y + block / 2f;
		float small = font(SMALL);
		text(d, LABEL, "Bind:", x, cy, label(entry.settingsOn()), small);
		int key = entry.module.keybind();
		String label = listening ? "Press a key..." : key > 0 ? keyName(key) : "None";
		int chipW = width(d, VALUE, label, small) + px(16);
		int chipH = px(20);
		int cx = x + w - chipW;
		int chipY = Math.round(cy - chipH / 2f);
		boolean hover = mx >= cx && mx < x + w && my >= chipY && my < chipY + chipH;
		int fill = listening ? AmethystTheme.withAlpha(t.accent(), 0.18f) : hover ? row(t, 1f) : inset(t);
		Ui.smooth(d, cx, chipY, chipW, chipH, px(6), fill);
		int color = listening ? t.accent() : key > 0 ? label(entry.settingsOn()) : OPTION;
		text(d, VALUE, label, cx + px(8), cy, color, small);
		Runnable start = () -> binding = listening ? null : entry;
		addHit(Hit.click(x, y, x + w, y + block, start, () -> {
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

	/** Above the columns: the search text while typing, or what the keybind is waiting for. */
	private void renderTopBar(Draw d, AmethystTheme t) {
		if (query.isEmpty() && binding == null) {
			return;
		}
		float small = font(SMALL + 0.1f);
		String label = binding != null
				? "Press a key for " + binding.name + "  ·  Backspace clears  ·  Esc cancels"
				: query;
		int icon = binding != null ? 0 : iconSize(Glyphs.SEARCH);
		int pad = px(16);
		int w = Math.max(px(240), width(d, LABEL, label, small) + pad * 2 + (icon > 0 ? icon + px(10) : 0) + px(6));
		int h = px(38);
		int x = (screenWidth() - w) / 2;
		int top = columns.isEmpty() ? px(20) : columns.getFirst().y;
		int y = Math.max(px(6), top - h - px(14));
		Ui.smoothShadow(d, x, y, w, h, h / 2, px(8), 0.3f);
		Ui.smooth(d, x, y, w, h, h / 2, header(t));
		int tx = x + pad;
		float cy = y + h / 2f;
		if (icon > 0) {
			Glyphs.draw(d, Glyphs.SEARCH, tx, Math.round(cy - icon / 2f), icon, t.accent());
			tx += icon + px(10);
		}
		text(d, LABEL, label, tx, cy, binding != null ? t.accent() : TEXT, small);
		if (binding == null && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = tx + width(d, LABEL, label, small) + px(2);
			d.fill(cx, Math.round(cy - px(8)), cx + Math.max(1, px(2)), Math.round(cy + px(8)), t.accent());
		}
	}

	private void renderTooltip(Draw d, AmethystTheme t, Entry entry, int mx, int my) {
		float small = font(TOOLTIP);
		List<String> lines = wrapText(d, entry.description, px(260), small);
		int pad = px(12);
		int lineH = px(20);
		int w = width(d, MODULE, entry.name, small) + pad * 2;
		for (String line : lines) {
			w = Math.max(w, width(d, LABEL, line, small) + pad * 2);
		}
		int h = pad * 2 + lineH * (lines.size() + 1);
		int x = Math.min(mx + px(14), screenWidth() - w - px(6));
		int y = Math.min(my + px(18), screenHeight() - h - px(6));
		Ui.smoothShadow(d, x, y, w, h, px(8), px(8), 0.35f);
		Ui.smooth(d, x, y, w, h, px(8), header(t));
		float ly = y + pad + lineH / 2f;
		text(d, MODULE, entry.name, x + pad, ly, t.accent(), small);
		for (String line : lines) {
			ly += lineH;
			text(d, LABEL, line, x + pad, ly, OPTION, small);
		}
	}

	/** {@code text} split on spaces into lines of {@link #LABEL} text no wider than {@code maxWidth}. */
	private static List<String> wrapText(Draw d, String text, int maxWidth, float scale) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && width(d, LABEL, next, scale) > maxWidth) {
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

	// ---- layout mode (GUI pixels) ----

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
		Ui.round(d, (width - hw) / 2, 8, hw, 18, 6, AmethystTheme.withAlpha(t.bgSidebar(), 0.94f));
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

		double sx = mx * guiScale;
		double sy = my * guiScale;
		// Later hits are drawn on top (the dot over its row), so search from the end.
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (!hit.contains(sx, sy)) {
				continue;
			}
			if (hit.slider() != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				draggingSlider = hit;
				hit.slider().setFraction((sx - hit.trackX()) / hit.trackW());
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
		if (draggingSlider != null) {
			draggingSlider.slider().setFraction((mx * guiScale - draggingSlider.trackX()) / draggingSlider.trackW());
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
		draggingModule = null;
		snappedCenterX = false;
		snappedCenterY = false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		if (layoutMode) {
			return true;
		}
		double sx = mx * guiScale;
		double sy = my * guiScale;
		for (Column c : columns) {
			if (sx >= c.x && sx < c.x + px(COLUMN_WIDTH) && sy >= c.y && sy < c.y + px(COLUMN_HEIGHT)) {
				c.scroll = Math.clamp(c.scroll - amount * px(46), 0, maxScroll(c));
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
