package com.amethystclient.scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

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
	private static final Comparator<ScoreboardEntry> ORDER = Comparator.comparing(ScoreboardEntry::value)
			.reversed()
			.thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);
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

	public static void render(DrawContext context, ScoreboardObjective objective) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		Scoreboard scoreboard = objective.getScoreboard();

		List<Text> body = new ArrayList<>();
		for (ScoreboardEntry score : scoreboard.getScoreboardEntries(objective).stream()
				.filter(score -> !score.hidden())
				.sorted(ORDER)
				.limit(MAX_LINES)
				.toList()) {
			body.add(Team.decorateName(scoreboard.getScoreHolderTeam(score.owner()), score.name()));
		}

		Text header = objective.getDisplayName();
		if (isBlank(header)) {
			header = null;
		}
		Text footer = null;
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
			textWidth = font.getWidth(header);
		}
		if (footer != null) {
			textWidth = Math.max(textWidth, font.getWidth(footer));
		}
		for (Text line : body) {
			textWidth = Math.max(textWidth, font.getWidth(line));
		}
		int width = textWidth + PADDING_X * 2;

		int singleHeight = font.fontHeight + PADDING_Y * 2;
		int bodyHeight = body.size() * LINE_HEIGHT - (LINE_HEIGHT - font.fontHeight) + PADDING_Y * 2;
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

		int right = context.getScaledWindowWidth() - SCREEN_MARGIN;
		int left = right - width;
		int y = (context.getScaledWindowHeight() - totalHeight) / 2;

		if (header != null) {
			panel(context, left, y, right, y + singleHeight, HEADER_BACKGROUND);
			centered(context, font, header, left, right, y + PADDING_Y);
			y += singleHeight + PANEL_GAP;
		}
		if (!body.isEmpty()) {
			panel(context, left, y, right, y + bodyHeight, BACKGROUND);
			for (int i = 0; i < body.size(); i++) {
				context.drawText(font, body.get(i), left + PADDING_X, y + PADDING_Y + i * LINE_HEIGHT, -1, true);
			}
			y += bodyHeight + PANEL_GAP;
		}
		if (footer != null) {
			panel(context, left, y, right, y + singleHeight, BACKGROUND);
			centered(context, font, footer, left, right, y + PADDING_Y);
		}
	}

	/** A filled panel with its corner pixels left out, so the corners look rounded. */
	private static void panel(DrawContext context, int x1, int y1, int x2, int y2, int color) {
		context.fill(x1 + 1, y1, x2 - 1, y2, color);
		context.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
		context.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	private static void centered(DrawContext context, TextRenderer font, Text text, int left, int right, int y) {
		context.drawText(font, text, (left + right - font.getWidth(text)) / 2, y, -1, true);
	}

	/**
	 * True when the line shows nothing. TAB's empty lines are often not plain spaces but invisible
	 * characters such as the Hangul filler "ㅤ", so those count as blank too.
	 */
	private static boolean isBlank(Text text) {
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
