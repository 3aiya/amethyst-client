package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.ClientModule;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Hides "X joined the game" style messages from chat (see ChatTweaks). */
public class HideJoinLeaveModule extends ClientModule {
	/** Vanilla's join/leave messages. */
	public static final Set<String> TRANSLATION_KEYS = Set.of(
			"multiplayer.player.joined", "multiplayer.player.joined.renamed", "multiplayer.player.left");

	/** The usual plugin formats: "[+] Name", "» Name joined the server", "Name has left", ... */
	private static final List<Pattern> PATTERNS = List.of(
			Pattern.compile("^\\W{0,3}\\s*[\\[(]?\\s*[+\\-]\\s*[\\])]?\\s*\\w{3,16}\\s*$"),
			Pattern.compile("^\\W{0,3}\\s*(\\[[^\\]]{1,16}]\\s*)?\\w{3,16}\\s+(has\\s+)?(joined|left|quit)"
					+ "(\\s+the\\s+(game|server|network|lobby|hub))?\\s*[.!]?\\s*$", Pattern.CASE_INSENSITIVE));

	public HideJoinLeaveModule() {
		super("hide_join_leave", "Hide Join/Leave", "Hides the messages when players join or leave", Category.SERVER, false);
	}

	public boolean hides(String plainText) {
		String text = plainText.strip();
		return PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).matches());
	}
}
