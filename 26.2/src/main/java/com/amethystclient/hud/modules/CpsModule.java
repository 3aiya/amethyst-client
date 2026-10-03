package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.Game;
import com.amethystclient.hud.setting.ModeSetting;
import java.util.ArrayDeque;

/** Clicks per second for the left and right mouse buttons. */
public class CpsModule extends SimpleModule {
	private static final ClickCounter LEFT = new ClickCounter(0);
	private static final ClickCounter RIGHT = new ClickCounter(1);

	private final ModeSetting buttons;

	public CpsModule() {
		super("cps", "CPS", "Clicks per second", Category.INFO, false, Position.topLeft(51));
		buttons = mode("buttons", "Buttons", "Both", "Left", "Both");
	}

	/** Polls the mouse buttons; called every frame while in game. */
	public static void update() {
		boolean counting = !Game.screenOpen();
		LEFT.update(counting);
		RIGHT.update(counting);
	}

	public static int left() {
		return LEFT.cps();
	}

	public static int right() {
		return RIGHT.cps();
	}

	@Override
	protected String label() {
		return "CPS";
	}

	@Override
	protected String value() {
		return buttons.get().equals("Left") ? Integer.toString(left()) : left() + " §7|§r " + right();
	}

	@Override
	protected String widthSample() {
		return buttons.get().equals("Left") ? "00" : "00 | 00";
	}

	private static final class ClickCounter {
		private final int button;
		private final ArrayDeque<Long> clicks = new ArrayDeque<>();
		private boolean wasDown;

		ClickCounter(int button) {
			this.button = button;
		}

		void update(boolean counting) {
			boolean down = Game.mouseButtonDown(button);
			if (down && !wasDown && counting) {
				clicks.addLast(System.currentTimeMillis());
			}
			wasDown = down;
		}

		int cps() {
			long cutoff = System.currentTimeMillis() - 1000;
			while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
				clicks.removeFirst();
			}
			return clicks.size();
		}
	}
}
