package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.Game;
import com.amethystclient.hud.HudModule;
import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.hud.setting.SliderSetting;
import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Motion;
import com.amethystclient.ui.Ui;
import java.util.EnumMap;
import java.util.Map;

/** W A S D, the mouse buttons and the space bar; a pressed key fills with the accent. */
public class KeystrokesModule extends HudModule {
	private static final int GAP = 2;
	private static final int SPACE_HEIGHT = 10;

	private final Map<Game.Key, Motion> fades = new EnumMap<>(Game.Key.class);
	private final BoolSetting mouse;
	private final BoolSetting space;
	private final SliderSetting size;

	public KeystrokesModule() {
		super("keystrokes", "Keystrokes", "Movement keys and clicks", Category.PLAYER, false, new Position(0, 0.5, MARGIN, 0));
		for (Game.Key key : Game.Key.values()) {
			fades.put(key, new Motion(0f, 80));
		}
		mouse = bool("mouse", "Mouse buttons", true);
		space = bool("space", "Space bar", true);
		size = slider("size", "Key size", 16, 28, 1, 20, v -> (int) v + " px");
	}

	private int key() {
		return (int) size.get();
	}

	@Override
	public void measure(Draw d, boolean preview) {
		int key = key();
		width = key * 3 + GAP * 2;
		height = key * 2 + GAP;
		if (mouse.get()) {
			height += GAP + key;
		}
		if (space.get()) {
			height += GAP + SPACE_HEIGHT;
		}
	}

	@Override
	public void render(Draw d, AmethystTheme t, int x, int y, boolean preview) {
		int key = key();
		int half = (width - GAP) / 2;
		key(d, t, Game.Key.FORWARD, "W", x + key + GAP, y, key, key);
		int row = y + key + GAP;
		key(d, t, Game.Key.LEFT, "A", x, row, key, key);
		key(d, t, Game.Key.BACK, "S", x + key + GAP, row, key, key);
		key(d, t, Game.Key.RIGHT, "D", x + (key + GAP) * 2, row, key, key);
		row += key + GAP;
		if (mouse.get()) {
			key(d, t, Game.Key.ATTACK, cpsLabel("LMB", CpsModule.left()), x, row, half, key);
			key(d, t, Game.Key.USE, cpsLabel("RMB", CpsModule.right()), x + half + GAP, row, width - half - GAP, key);
			row += key + GAP;
		}
		if (space.get()) {
			key(d, t, Game.Key.JUMP, null, x, row, width, SPACE_HEIGHT);
		}
	}

	/** The button name, or its clicks per second while clicking. */
	private static String cpsLabel(String name, int cps) {
		return cps > 0 ? Integer.toString(cps) : name;
	}

	private void key(Draw d, AmethystTheme t, Game.Key key, String label, int x, int y, int w, int h) {
		Motion fade = fades.get(key);
		fade.set(Game.inWorld() && Game.keyDown(key) ? 1f : 0f);
		float p = fade.get();
		int fill = AmethystTheme.lerp(AmethystTheme.withAlpha(t.bgCard(), backgroundAlpha()), t.accent(), p);
		int border = AmethystTheme.lerp(AmethystTheme.withAlpha(t.border(), Math.max(backgroundAlpha(), 0.4f)), t.accent(), p);
		Ui.box(d, x, y, w, h, Ui.RADIUS_BUTTON, fill, border);
		int text = AmethystTheme.lerp(AmethystTheme.TEXT_HEADLINE, t.accentFg(), p);
		if (label != null) {
			Ui.centered(d, label, x + w / 2, y + (h - d.lineHeight()) / 2 + 1, text);
		} else {
			d.fill(x + w / 2 - 8, y + h / 2, x + w / 2 + 8, y + h / 2 + 1, text);
		}
	}
}
