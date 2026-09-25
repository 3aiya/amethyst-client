package com.amethystclient;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** Recognises the Amethyst Community servers, which get the mod's server-specific features. */
public final class AmethystServers {
	/** The address shown to other people (Discord), whatever address the player typed. */
	public static final String DISPLAY_ADDRESS = "mc.amethystcommunity.net";

	/**
	 * A server matches when its address is one of these hosts (the port is ignored) or, for a
	 * domain, a subdomain of one.
	 */
	private static final List<String> HOSTS = List.of(
			"amethystcommunity.net",
			"156.67.217.177"
	);

	private AmethystServers() {
	}

	public static boolean isAmethystServer(Minecraft client) {
		ServerData server = client.getCurrentServer();
		if (server == null || server.ip == null) {
			return false;
		}
		String host = hostOf(server.ip);
		for (String amethyst : HOSTS) {
			if (host.equals(amethyst) || host.endsWith("." + amethyst)) {
				return true;
			}
		}
		return false;
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
