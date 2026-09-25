package com.amethystclient.presence;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

/**
 * Answers Amethyst Client's "which mode am I in?" question with the name of the player's current
 * backend server, so the client can show "Amethyst Community - Survival" in Discord.
 *
 * <p>The client asks by sending an empty string on {@value #CHANNEL} whenever it gets a new world
 * (join, server switch, dimension change). The reply is a Minecraft string: a VarInt byte length
 * followed by UTF-8. Players without the mod never ask, so they never get anything.
 */
public final class AmethystPresencePlugin extends Plugin implements Listener {
	static final String CHANNEL = "amethyst:presence";

	private final Map<String, String> modes = new HashMap<>();

	@Override
	public void onEnable() {
		loadConfig();
		getProxy().registerChannel(CHANNEL);
		getProxy().getPluginManager().registerListener(this, this);
	}

	@Override
	public void onDisable() {
		getProxy().unregisterChannel(CHANNEL);
	}

	@EventHandler
	public void onPluginMessage(PluginMessageEvent event) {
		if (!CHANNEL.equals(event.getTag())) {
			return;
		}
		// Handled here only: never forward it, and never let a backend send it to a client.
		event.setCancelled(true);
		if (!(event.getSender() instanceof ProxiedPlayer player)) {
			return;
		}
		Server server = player.getServer();
		if (server == null) {
			return;
		}
		player.sendData(CHANNEL, encodeString(modeName(server.getInfo().getName())));
	}

	private String modeName(String serverName) {
		String configured = modes.get(serverName.toLowerCase(Locale.ROOT));
		if (configured != null) {
			return configured;
		}
		return serverName.isEmpty() ? serverName : Character.toUpperCase(serverName.charAt(0)) + serverName.substring(1);
	}

	private void loadConfig() {
		try {
			File folder = getDataFolder();
			File file = new File(folder, "config.yml");
			if (!file.exists()) {
				folder.mkdirs();
				try (InputStream defaults = getResourceAsStream("config.yml")) {
					Files.copy(defaults, file.toPath());
				}
			}
			Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
			Configuration section = config.getSection("modes");
			modes.clear();
			for (String key : section.getKeys()) {
				modes.put(key.toLowerCase(Locale.ROOT), section.getString(key));
			}
		} catch (IOException e) {
			getLogger().log(Level.WARNING, "Could not load config.yml, using server names as mode names", e);
		}
	}

	/** A Minecraft protocol string: VarInt byte length, then UTF-8. */
	private static byte[] encodeString(String value) {
		byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream out = new ByteArrayOutputStream(utf8.length + 5);
		int length = utf8.length;
		while ((length & ~0x7F) != 0) {
			out.write((length & 0x7F) | 0x80);
			length >>>= 7;
		}
		out.write(length);
		out.writeBytes(utf8);
		return out.toByteArray();
	}
}
