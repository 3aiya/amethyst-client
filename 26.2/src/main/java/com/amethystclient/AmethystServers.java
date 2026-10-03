package com.amethystclient;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.jetbrains.annotations.Nullable;

/** Recognises the Amethyst Community servers, which get the mod's server-specific features. */
public final class AmethystServers {
	/** The address shown to other people (Discord), whatever address the player typed. */
	public static final String DISPLAY_ADDRESS = "mc.amethystcommunity.net";

	/** The name of the server pinned to the top of the multiplayer list. */
	public static final String PINNED_NAME = "Amethyst Community";

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
		return isAmethystServer(client.getCurrentServer());
	}

	public static boolean isAmethystServer(@Nullable ServerData server) {
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

	/**
	 * Puts the Amethyst server at the top of a freshly loaded server list: the player's own entry
	 * if they have one (from the visible list, else the direct-connect history), otherwise a new one.
	 */
	public static void pinToTop(List<ServerData> servers, List<ServerData> hiddenServers) {
		ServerData pinned = find(servers);
		if (pinned != null) {
			servers.remove(pinned);
		} else {
			pinned = find(hiddenServers);
			if (pinned != null) {
				hiddenServers.remove(pinned);
			} else {
				pinned = new ServerData(PINNED_NAME, DISPLAY_ADDRESS, ServerData.Type.OTHER);
			}
		}
		pinned.name = PINNED_NAME;
		servers.add(0, pinned);
	}

	/** True for the list's pinned entry, which can't be moved, edited or deleted. */
	public static boolean isPinned(ServerList list, @Nullable ServerData server) {
		return server != null && hasPinned(list) && list.get(0) == server;
	}

	/** False when the swap would move the pinned entry or put another one above it. */
	public static boolean canSwap(ServerList list, int a, int b) {
		return !hasPinned(list) || (a != 0 && b != 0);
	}

	public static boolean canMoveUp(ServerList list, ServerData server) {
		return !hasPinned(list) || indexOf(list, server) > 1;
	}

	public static boolean canMoveDown(ServerList list, ServerData server) {
		return !isPinned(list, server);
	}

	private static boolean hasPinned(ServerList list) {
		return list.size() > 0 && isAmethystServer(list.get(0));
	}

	private static int indexOf(ServerList list, ServerData server) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) == server) {
				return i;
			}
		}
		return -1;
	}

	@Nullable
	private static ServerData find(List<ServerData> servers) {
		for (ServerData server : servers) {
			if (isAmethystServer(server)) {
				return server;
			}
		}
		return null;
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
