package com.amethystclient.scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
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
	/**
	 * Servers that get the styled sidebar. A server matches when its address is one of these
	 * hosts (the port is ignored) or, for a domain, a subdomain of one.
	 */
	private static final List<String> STYLED_SERVERS = List.of(
			"amethystcommunity.net",
			"156.67.217.177"
	);

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

	private static final int BACKGROUND = 0xCC1B2614;
	private static final int BORDER = 0xFFA6D86C;
	private static final int INNER_BORDER = 0x55A6D86C;

	private StyledScoreboard() {
	}

	public static boolean isStyledServer(Minecraft client) {
		ServerData server = client.getCurrentServer();
		if (server == null || server.ip == null) {
			return false;
		}
		String host = hostOf(server.ip);
		for (String styled : STYLED_SERVERS) {
			if (host.equals(styled) || host.endsWith("." + styled)) {
				return true;
			}
		}
		return false;
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
			panel(graphics, left, y, right, y + singleHeight);
			centered(graphics, font, header, left, right, y + PADDING_Y);
			y += singleHeight + PANEL_GAP;
		}
		if (!body.isEmpty()) {
			panel(graphics, left, y, right, y + bodyHeight);
			for (int i = 0; i < body.size(); i++) {
				graphics.text(font, body.get(i), left + PADDING_X, y + PADDING_Y + i * LINE_HEIGHT, -1, true);
			}
			y += bodyHeight + PANEL_GAP;
		}
		if (footer != null) {
			panel(graphics, left, y, right, y + singleHeight);
			centered(graphics, font, footer, left, right, y + PADDING_Y);
		}
	}

	/** A translucent panel with a rounded 1px border and a faint inner line. */
	private static void panel(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
		graphics.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, BACKGROUND);
		graphics.fill(x1 + 1, y1, x2 - 1, y1 + 1, BORDER);
		graphics.fill(x1 + 1, y2 - 1, x2 - 1, y2, BORDER);
		graphics.fill(x1, y1 + 1, x1 + 1, y2 - 1, BORDER);
		graphics.fill(x2 - 1, y1 + 1, x2, y2 - 1, BORDER);
		graphics.fill(x1 + 3, y1 + 2, x2 - 3, y1 + 3, INNER_BORDER);
		graphics.fill(x1 + 3, y2 - 3, x2 - 3, y2 - 2, INNER_BORDER);
		graphics.fill(x1 + 2, y1 + 3, x1 + 3, y2 - 3, INNER_BORDER);
		graphics.fill(x2 - 3, y1 + 3, x2 - 2, y2 - 3, INNER_BORDER);
	}

	private static void centered(GuiGraphicsExtractor graphics, Font font, Component text, int left, int right, int y) {
		graphics.text(font, text, (left + right - font.width(text)) / 2, y, -1, true);
	}

	private static boolean isBlank(Component text) {
		return text.getString().isBlank();
	}

	/** "Play.Example.com:25565" -> "play.example.com", "[::1]:25565" -> "::1". */
	private static String hostOf(String address) {
		String host = address.trim();
		if (host.startsWith("[")) {
			int end = host.indexOf(']');
			host = end > 0 ? host.substring(1, end) : host.substring(1);
		} else if (host.indexOf(':') == host.lastIndexOf(':') && host.indexOf(':') >= 0) {
			host = host.substring(0, host.indexOf(':'));
		}
		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		return host.toLowerCase(Locale.ROOT);
	}
}
