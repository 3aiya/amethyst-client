package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.ClientModule;
import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.hud.setting.ModeSetting;
import com.amethystclient.hud.setting.SliderSetting;

/**
 * The Amethyst panels in place of the vanilla sidebar scoreboard (see StyledScoreboard). Off =
 * the vanilla sidebar.
 */
public class StyledScoreboardModule extends ClientModule {
	private final ModeSetting side;
	private final BoolSetting title;
	private final BoolSetting footer;
	private final SliderSetting background;
	private final BoolSetting everywhere;

	public StyledScoreboardModule() {
		super("styled_scoreboard", "Styled Scoreboard", "The Amethyst panels instead of the vanilla sidebar",
				Category.SERVER, true);
		side = mode("side", "Side", "Right", "Right", "Left");
		title = bool("title", "Show title", true);
		footer = bool("footer", "Show footer", true);
		background = slider("background", "Background", 0, 1, 0.05, 0.8, v -> Math.round(v * 100) + "%");
		everywhere = bool("everywhere", "On every server", false);
	}

	/** True when the styled sidebar replaces the vanilla one on this server. */
	public boolean replaces(boolean amethystServer) {
		return enabled() && (amethystServer || everywhere.get());
	}

	public boolean leftSide() {
		return side.get().equals("Left");
	}

	public boolean showTitle() {
		return title.get();
	}

	public boolean showFooter() {
		return footer.get();
	}

	/** The panels' background colour with the chosen opacity. */
	public int background(int rgb) {
		return (int) Math.round(background.get() * 255) << 24 | rgb & 0xFFFFFF;
	}
}
