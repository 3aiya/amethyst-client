package com.amethystclient.hud;

import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Motion;
import com.amethystclient.ui.Page;
import com.amethystclient.ui.Ui;
import com.amethystclient.ui.widget.ChipSelect;
import com.amethystclient.ui.widget.GhostButton;
import com.amethystclient.ui.widget.FlatButton;
import com.amethystclient.ui.widget.Slider;
import com.amethystclient.ui.widget.Toggle;
import com.amethystclient.ui.widget.Widget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.glfw.GLFW;

/**
 * The HUD settings, opened with Right Shift. Two modes:
 * <ul>
 *   <li>settings: a panel with a category rail on the left (sliding accent pill) and the selected
 *   category's settings in the content well on the right (guide §5.12–§5.14, §10 "ClickGUI");</li>
 *   <li>layout: the panel goes away and the modules can be dragged around the screen.</li>
 * </ul>
 */
public class HudSettingsPage extends Page {
	private enum Category {
		HUD("HUD"), APPEARANCE("Theme");

		final String label;

		Category(String label) {
			this.label = label;
		}
	}

	private static final int PANEL_WIDTH = 320;
	private static final int PANEL_HEIGHT = 214;
	private static final int HEADER = 24;
	private static final int RAIL = 56;
	private static final int NAV_HEIGHT = 18;
	private static final int NAV_GAP = 3;
	private static final int PAD = 6;
	private static final int WELL_PAD = 10;
	private static final int BOTTOM_BAR = 28;
	private static final int ROW = 20;
	private static final int ROW_GAP = 3;
	private static final int SNAP = 4;

	private final HudConfig config = HudConfig.get();
	private final Motion open = new Motion(0f, 150);
	private final Motion pill = new Motion(0f, 200);
	private final Motion layoutFade = new Motion(0f, 160);

	private Category category = Category.HUD;
	private boolean layoutMode;
	private double scroll;
	private int contentHeight;

	// Widgets; positioned again every frame by layout().
	private final Map<HudModule, Toggle> moduleToggles = new HashMap<>();
	private final ChipSelect theme;
	private final Slider opacity;
	private final Slider scale;
	private final Toggle reduceMotion;
	private final GhostButton editLayout;
	private final GhostButton reset;
	private final FlatButton done;
	private final FlatButton layoutDone;
	private final List<Widget> active = new ArrayList<>();
	private Widget pressedWidget;

	// Layout of the current frame.
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int wellX;
	private int wellY;
	private int wellW;
	private int wellH;
	private final List<int[]> moduleRows = new ArrayList<>();

	// Dragging in layout mode.
	private HudModule dragging;
	private double grabX;
	private double grabY;
	private boolean snappedCenterX;
	private boolean snappedCenterY;

	public HudSettingsPage() {
		for (HudModule module : AmethystHud.MODULES) {
			moduleToggles.put(module, new Toggle(() -> module.settings().enabled, on -> module.settings().enabled = on));
		}
		List<String> names = new ArrayList<>();
		List<Integer> swatches = new ArrayList<>();
		for (AmethystTheme t : AmethystTheme.ALL) {
			names.add(t.name());
			swatches.add(t.accent());
		}
		theme = new ChipSelect(names, swatches, () -> config.theme, name -> config.theme = name);
		opacity = new Slider(0, 1, 0.05, () -> config.backgroundOpacity, v -> config.backgroundOpacity = v,
				v -> Math.round(v * 100) + "%");
		scale = new Slider(0.5, 2, 0.05, () -> config.scale, v -> config.scale = v,
				v -> Math.round(v * 100) + "%");
		reduceMotion = new Toggle(() -> config.reduceMotion, on -> config.reduceMotion = on);
		editLayout = new GhostButton("Edit layout", () -> setLayoutMode(true));
		reset = new GhostButton("Reset", this::resetLayout);
		done = new FlatButton("Done", FlatButton.Variant.PRIMARY, this::close);
		layoutDone = new FlatButton("Done", FlatButton.Variant.PRIMARY, () -> setLayoutMode(false));
		open.set(1f);
	}

	private void setLayoutMode(boolean on) {
		layoutMode = on;
		layoutFade.set(on ? 1f : 0f);
		dragging = null;
		pressedWidget = null;
	}

	private void resetLayout() {
		for (HudModule module : AmethystHud.MODULES) {
			module.resetPosition();
		}
	}

	@Override
	public void removed() {
		config.save();
	}

	// ---- layout ----

	private void layout() {
		pw = Math.min(PANEL_WIDTH, width - 24);
		ph = Math.min(PANEL_HEIGHT, height - 24);
		px = (width - pw) / 2;
		py = (height - ph) / 2 + Math.round((1f - open.get()) * 8);
		wellX = px + PAD + RAIL + PAD;
		wellY = py + HEADER;
		wellW = px + pw - PAD - wellX;
		wellH = py + ph - PAD - wellY;

		active.clear();
		moduleRows.clear();
		int x = wellX + WELL_PAD;
		int w = wellW - WELL_PAD * 2;
		int top = wellY + WELL_PAD - (int) scroll;
		int y = top;
		if (category == Category.HUD) {
			y += sectionGap();
			for (HudModule module : AmethystHud.MODULES) {
				moduleRows.add(new int[] {x, y, w, ROW});
				Toggle toggle = moduleToggles.get(module);
				toggle.at(x + w - 6 - Toggle.WIDTH, y + (ROW - Toggle.HEIGHT) / 2, Toggle.WIDTH, Toggle.HEIGHT);
				active.add(toggle);
				y += ROW + ROW_GAP;
			}
		} else {
			y += sectionGap();
			theme.at(x, y, w, 16);
			active.add(theme);
			y += 16 + 12;
			y += sectionGap();
			int labelWidth = 74;
			opacity.at(x + labelWidth, y + 5, w - labelWidth, 10);
			active.add(opacity);
			y += ROW;
			scale.at(x + labelWidth, y + 5, w - labelWidth, 10);
			active.add(scale);
			y += ROW;
			reduceMotion.at(x + w - Toggle.WIDTH, y + (ROW - Toggle.HEIGHT) / 2, Toggle.WIDTH, Toggle.HEIGHT);
			active.add(reduceMotion);
			y += ROW;
		}
		contentHeight = y - top;

		int barY = wellY + wellH - BOTTOM_BAR;
		editLayout.at(wellX + WELL_PAD, barY + 7, 66, 16);
		reset.at(wellX + WELL_PAD + 66 + 4, barY + 7, 44, 16);
		done.at(wellX + wellW - WELL_PAD - 58, barY + 5, 58, 20);
		active.add(editLayout);
		active.add(reset);
		active.add(done);
	}

	private static int sectionGap() {
		return 9 + 7;
	}

	private int contentTop() {
		return wellY + 1;
	}

	private int contentBottom() {
		return wellY + wellH - BOTTOM_BAR;
	}

	private double maxScroll() {
		return Math.max(0, contentHeight + WELL_PAD * 2 - (contentBottom() - contentTop()));
	}

	// ---- rendering ----

	@Override
	public void render(Draw d, int mx, int my) {
		AmethystTheme t = AmethystTheme.current();
		AmethystHud.renderModules(d, true);
		d.layer();

		float fade = layoutFade.get();
		if (fade > 0f) {
			renderLayoutOverlay(d, t, mx, my, fade);
		}
		if (fade < 1f) {
			d.alpha(open.get() * (1f - fade));
			renderSettings(d, t, mx, my);
			d.alpha(1f);
		}
	}

	private void renderSettings(Draw d, AmethystTheme t, int mx, int my) {
		if (layoutMode) {
			// Fading out: no hover states.
			mx = my = -1;
		}
		layout();
		d.fill(0, 0, width, height, AmethystTheme.BACKDROP);

		// Chrome.
		Ui.box(d, px - 1, py - 1, pw + 2, ph + 2, Ui.RADIUS_PANEL + 1, 0x00000000, 0x66000000);
		Ui.box(d, px, py, pw, ph, Ui.RADIUS_PANEL, t.bgSidebar(), t.border());
		Ui.wordmark(d, t, px + PAD + 4, py + 9);
		String hint = "Esc to close";
		d.text(hint, px + pw - PAD - 4 - d.textWidth(hint), py + 9, AmethystTheme.TEXT_FAINT);

		// Category rail with one sliding pill.
		Category[] categories = Category.values();
		pill.set(category.ordinal());
		int railY = py + HEADER;
		int pillY = railY + Math.round(pill.get() * (NAV_HEIGHT + NAV_GAP));
		Ui.round(d, px + PAD, pillY, RAIL, NAV_HEIGHT, Ui.RADIUS_BUTTON + 1, AmethystTheme.withAlpha(t.accent(), 0.15f));
		for (Category c : categories) {
			int cy = railY + c.ordinal() * (NAV_HEIGHT + NAV_GAP);
			boolean hover = mx >= px + PAD && mx < px + PAD + RAIL && my >= cy && my < cy + NAV_HEIGHT;
			int color = c == category ? t.accent() : hover ? AmethystTheme.TEXT_HOVER : AmethystTheme.TEXT_MUTED;
			Ui.centered(d, c.label, px + PAD + RAIL / 2, cy + (NAV_HEIGHT - d.lineHeight()) / 2 + 1, color);
		}

		// Content well.
		Ui.box(d, wellX, wellY, wellW, wellH, Ui.RADIUS_CARD + 2, t.bg(), t.border());
		boolean inContent = my >= contentTop() && my < contentBottom();
		int cmx = inContent ? mx : -1;
		int cmy = inContent ? my : -1;
		d.clip(wellX + 1, contentTop(), wellX + wellW - 1, contentBottom());
		if (category == Category.HUD) {
			renderModuleList(d, t, cmx, cmy);
		} else {
			renderAppearance(d, t, cmx, cmy);
		}
		d.unclip();
		renderScrollbar(d, t);

		// Bottom bar.
		int barY = contentBottom();
		d.fill(wellX + 1, barY, wellX + wellW - 1, barY + 1, t.border());
		editLayout.render(d, t, mx, my);
		reset.render(d, t, mx, my);
		done.render(d, t, mx, my);
	}

	private void renderModuleList(Draw d, AmethystTheme t, int mx, int my) {
		int x = wellX + WELL_PAD;
		Ui.sectionTitle(d, t, "HUD modules", x, wellY + WELL_PAD - (int) scroll);
		for (int i = 0; i < AmethystHud.MODULES.size(); i++) {
			HudModule module = AmethystHud.MODULES.get(i);
			int[] r = moduleRows.get(i);
			boolean hover = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
			Ui.card(d, t, r[0], r[1], r[2], r[3], hover);
			int ty = r[1] + (r[3] - d.lineHeight()) / 2 + 1;
			String name = module.name;
			d.text(name, r[0] + 7, ty, module.enabled() ? AmethystTheme.TEXT_HEADLINE : AmethystTheme.TEXT_LABEL);
			int descX = r[0] + 7 + d.textWidth(name) + 6;
			int descMax = r[0] + r[2] - 6 - Toggle.WIDTH - 6 - descX;
			if (descMax > 20) {
				d.text(Ui.clip(d, module.description, descMax), descX, ty, AmethystTheme.TEXT_MUTED);
			}
			moduleToggles.get(module).render(d, t, mx, my);
		}
	}

	private void renderAppearance(Draw d, AmethystTheme t, int mx, int my) {
		int x = wellX + WELL_PAD;
		int y = wellY + WELL_PAD - (int) scroll;
		Ui.sectionTitle(d, t, "Theme", x, y);
		theme.render(d, t, mx, my);
		y = theme.y + theme.height + 12;
		Ui.sectionTitle(d, t, "HUD", x, y);
		label(d, "Background", x, opacity.y);
		opacity.render(d, t, mx, my);
		label(d, "Scale", x, scale.y);
		scale.render(d, t, mx, my);
		label(d, "Reduce motion", x, reduceMotion.y + 1);
		reduceMotion.render(d, t, mx, my);
	}

	private static void label(Draw d, String text, int x, int y) {
		d.text(text, x, y + 1, AmethystTheme.TEXT_PRIMARY);
	}

	private void renderScrollbar(Draw d, AmethystTheme t) {
		double max = maxScroll();
		if (max <= 0) {
			return;
		}
		int trackTop = contentTop() + 3;
		int trackHeight = contentBottom() - 3 - trackTop;
		int thumb = Math.max(12, (int) (trackHeight * trackHeight / (double) (trackHeight + max)));
		int thumbY = trackTop + (int) ((trackHeight - thumb) * (scroll / max));
		Ui.round(d, wellX + wellW - 5, thumbY, 2, thumb, 1, t.borderLight());
	}

	private void renderLayoutOverlay(Draw d, AmethystTheme t, int mx, int my, float fade) {
		d.alpha(fade);
		float s = AmethystHud.scale();
		int hudW = AmethystHud.hudWidth(d);
		int hudH = AmethystHud.hudHeight(d);
		d.fill(0, 0, width, height, 0x33000000);

		if (dragging != null && snappedCenterX) {
			d.fill(width / 2, 0, width / 2 + 1, height, AmethystTheme.withAlpha(t.accent(), 0.5f));
		}
		if (dragging != null && snappedCenterY) {
			d.fill(0, height / 2, width, height / 2 + 1, AmethystTheme.withAlpha(t.accent(), 0.5f));
		}

		HudModule hovered = layoutMode && dragging == null ? moduleAt(mx, my) : null;
		for (HudModule module : AmethystHud.MODULES) {
			if (!module.enabled() || module.width == 0) {
				continue;
			}
			int x = Math.round(module.x(hudW) * s) - 2;
			int y = Math.round(module.y(hudH) * s) - 2;
			int w = Math.round(module.width * s) + 4;
			int h = Math.round(module.height * s) + 4;
			int color = module == dragging ? t.accent()
					: module == hovered ? AmethystTheme.withAlpha(t.accent(), 0.5f)
					: AmethystTheme.withAlpha(AmethystTheme.WHITE, 0.25f);
			Ui.outline(d, x, y, w, h, Ui.RADIUS_CARD, color);
			if (module == hovered || module == dragging) {
				String name = module.name;
				int ly = y > 12 ? y - 11 : y + h + 2;
				d.text(name, x + 1, ly, color);
			}
		}

		// Hint and Done button, top centre.
		String hint = "Drag modules to move them  ·  Esc when done";
		int hw = d.textWidth(hint) + 16;
		int hx = (width - hw) / 2;
		Ui.box(d, hx, 8, hw, 18, Ui.RADIUS_CARD, t.bgCard(), t.border());
		Ui.centered(d, hint, width / 2, 13, AmethystTheme.TEXT_SECONDARY);
		layoutDone.at((width - 64) / 2, 32, 64, 20);
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
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (layoutMode) {
			if (layoutDone.mouseClicked(mx, my)) {
				pressedWidget = layoutDone;
				return true;
			}
			HudModule module = moduleAt(mx, my);
			if (module != null) {
				float s = AmethystHud.scale();
				dragging = module;
				grabX = mx / s - module.x((int) (width / s));
				grabY = my / s - module.y((int) (height / s));
			}
			return true;
		}

		// Category rail.
		for (Category c : Category.values()) {
			int cy = py + HEADER + c.ordinal() * (NAV_HEIGHT + NAV_GAP);
			if (mx >= px + PAD && mx < px + PAD + RAIL && my >= cy && my < cy + NAV_HEIGHT) {
				if (category != c) {
					category = c;
					scroll = 0;
				}
				return true;
			}
		}

		boolean inContent = my >= contentTop() && my < contentBottom();
		for (Widget widget : active) {
			boolean inBottomBar = widget == editLayout || widget == reset || widget == done;
			if ((inContent || inBottomBar) && widget.mouseClicked(mx, my)) {
				pressedWidget = widget;
				return true;
			}
		}
		// A click anywhere on a module row toggles it.
		if (inContent && category == Category.HUD) {
			for (int i = 0; i < moduleRows.size(); i++) {
				int[] r = moduleRows.get(i);
				if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
					HudConfig.Module settings = AmethystHud.MODULES.get(i).settings();
					settings.enabled = !settings.enabled;
					return true;
				}
			}
		}
		if (mx < px || mx >= px + pw || my < py || my >= py + ph) {
			// Clicking the backdrop closes the panel (§5.14).
			close();
		}
		return true;
	}

	@Override
	public void mouseDragged(double mx, double my, int button) {
		if (pressedWidget != null) {
			pressedWidget.mouseDragged(mx, my);
			return;
		}
		if (dragging != null) {
			float s = AmethystHud.scale();
			int hudW = (int) (width / s);
			int hudH = (int) (height / s);
			int x = (int) Math.round(mx / s - grabX);
			int y = (int) Math.round(my / s - grabY);
			int centerX = (hudW - dragging.width) / 2;
			int centerY = (hudH - dragging.height) / 2;
			snappedCenterX = Math.abs(x - centerX) <= SNAP;
			snappedCenterY = Math.abs(y - centerY) <= SNAP;
			x = snappedCenterX ? centerX : snap(x, hudW - dragging.width);
			y = snappedCenterY ? centerY : snap(y, hudH - dragging.height);
			dragging.moveTo(Math.clamp(x, 0, Math.max(0, hudW - dragging.width)),
					Math.clamp(y, 0, Math.max(0, hudH - dragging.height)), hudW, hudH);
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
		if (pressedWidget != null) {
			Widget widget = pressedWidget;
			pressedWidget = null;
			widget.mouseReleased(mx, my);
		}
		dragging = null;
		snappedCenterX = false;
		snappedCenterY = false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		if (!layoutMode) {
			scroll = Math.clamp(scroll - amount * 12, 0, maxScroll());
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
