package com.amethystclient.scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Draws the sidebar scoreboard as the Amethyst Community panels on the community's own servers.
 * The server keeps sending a normal scoreboard (TAB plugin), so players without the mod see the
 * vanilla sidebar and nothing changes server side.
 *
 * <p>The scoreboard lines are split into three panels:
 * <ul>
 *   <li>header: the objective's title,</li>
 *   <li>body: every line in between,</li>
 *   <li>footer: the last line, when a blank line separates it from the body (e.g. the website).</li>
 * </ul>
 */
public final class StyledScoreboard {
	// Same order and limit as the vanilla sidebar.
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparing(PlayerScoreEntry::value)
			.reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static final int MAX_LINES = 15;

	private static final int LINE_HEIGHT = 11;
	private static final int PADDING_X = 6;
	private static final int PADDING_Y = 5;
	private static final int PANEL_GAP = 3;
	private static final int SCREEN_MARGIN = 3;

	// The vanilla sidebar's colours: 40% black behind the title, 30% black behind the lines.
	private static final int HEADER_BACKGROUND = 0x66000000;
	private static final int BACKGROUND = 0x4D000000;

	private StyledScoreboard() {
	}

	public static void extract(GuiGraphicsExtractor graphics, Objective objective) {
		Font font = Minecraft.getInstance().font;
		Scoreboard scoreboard = objective.getScoreboard();

		List<Component> body = new ArrayList<>();
		for (PlayerScoreEntry score : scoreboard.listPlayerScores(objective).stream()
				.filter(score -> !score.isHidden())
				.sorted(ORDER)
				.limit(MAX_LINES)
				.toList()) {
			body.add(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(score.owner()), score.ownerName()));
		}

		Component header = objective.getDisplayName();
		if (isBlank(header)) {
			header = null;
		}
		Component footer = null;
		int last = body.size() - 1;
		if (last >= 1 && !isBlank(body.get(last)) && isBlank(body.get(last - 1))) {
			footer = body.remove(last);
		}
		while (!body.isEmpty() && isBlank(body.getFirst())) {
			body.removeFirst();
		}
		while (!body.isEmpty() && isBlank(body.getLast())) {
			body.removeLast();
		}

		int textWidth = 0;
		if (header != null) {
			textWidth = font.width(header);
		}
		if (footer != null) {
			textWidth = Math.max(textWidth, font.width(footer));
		}
		for (Component line : body) {
			textWidth = Math.max(textWidth, font.width(line));
		}
		int width = textWidth + PADDING_X * 2;

		int singleHeight = font.lineHeight + PADDING_Y * 2;
		int bodyHeight = body.size() * LINE_HEIGHT - (LINE_HEIGHT - font.lineHeight) + PADDING_Y * 2;
		int totalHeight = 0;
		int panels = 0;
		if (header != null) {
			totalHeight += singleHeight;
			panels++;
		}
		if (!body.isEmpty()) {
			totalHeight += bodyHeight;
			panels++;
		}
		if (footer != null) {
			totalHeight += singleHeight;
			panels++;
		}
		if (panels == 0) {
			return;
		}
		totalHeight += (panels - 1) * PANEL_GAP;

		int right = graphics.guiWidth() - SCREEN_MARGIN;
		int left = right - width;
		int y = (graphics.guiHeight() - totalHeight) / 2;

		if (header != null) {
			panel(graphics, left, y, right, y + singleHeight, HEADER_BACKGROUND);
			centered(graphics, font, header, left, right, y + PADDING_Y);
			y += singleHeight + PANEL_GAP;
		}
		if (!body.isEmpty()) {
			panel(graphics, left, y, right, y + bodyHeight, BACKGROUND);
			for (int i = 0; i < body.size(); i++) {
				graphics.text(font, body.get(i), left + PADDING_X, y + PADDING_Y + i * LINE_HEIGHT, -1, true);
			}
			y += bodyHeight + PANEL_GAP;
		}
		if (footer != null) {
			panel(graphics, left, y, right, y + singleHeight, BACKGROUND);
			centered(graphics, font, footer, left, right, y + PADDING_Y);
		}
	}

	/** A filled panel with its corner pixels left out, so the corners look rounded. */
	private static void panel(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
		graphics.fill(x1 + 1, y1, x2 - 1, y2, color);
		graphics.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
		graphics.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	private static void centered(GuiGraphicsExtractor graphics, Font font, Component text, int left, int right, int y) {
		graphics.text(font, text, (left + right - font.width(text)) / 2, y, -1, true);
	}

	/**
	 * True when the line shows nothing. TAB's empty lines are often not plain spaces but invisible
	 * characters such as the Hangul filler "ㅤ", so those count as blank too.
	 */
	private static boolean isBlank(Component text) {
		return text.getString().codePoints().allMatch(StyledScoreboard::isInvisible);
	}

	private static boolean isInvisible(int c) {
		return Character.isWhitespace(c)
				|| Character.isSpaceChar(c)
				|| Character.getType(c) == Character.FORMAT
				|| c == 0x115F || c == 0x1160 || c == 0x3164 || c == 0xFFA0
				|| c == 0x2800;
	}
}
