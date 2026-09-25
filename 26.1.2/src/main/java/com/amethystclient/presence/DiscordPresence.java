package com.amethystclient.presence;

import com.google.gson.JsonObject;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the Discord Rich Presence in sync with what the game says it's doing. The game thread calls
 * {@link #set} as often as it likes; a background thread owns the Discord connection, reconnects
 * when Discord starts later, and only sends an update when something actually changed (Discord
 * allows about 5 updates per 20 seconds).
 */
public final class DiscordPresence {
	private static final Logger LOGGER = LoggerFactory.getLogger("amethystclient/presence");
	private static final String CLIENT_ID = "1525477067284676628";
	/** Asset key of the big picture, uploaded under Rich Presence > Art Assets in the Discord app. */
	private static final String LARGE_IMAGE = "logo";
	private static final String LARGE_TEXT = "Amethyst Community";

	private static final long POLL_MILLIS = 1000;
	private static final long MIN_UPDATE_INTERVAL_MILLIS = 4000;
	private static final long RECONNECT_DELAY_MILLIS = 15000;

	private record Activity(String details, String state, long startMillis) {
	}

	private static volatile Activity desired;
	private static boolean started;

	private DiscordPresence() {
	}

	public static synchronized void start() {
		if (started) {
			return;
		}
		started = true;
		Thread thread = new Thread(DiscordPresence::run, "amethystclient-discord");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Shows {@code details} (top line) and {@code state} (second line, may be null). The elapsed
	 * timer restarts whenever either of them changes.
	 */
	public static void set(String details, String state) {
		Activity current = desired;
		if (current != null && current.details.equals(details) && Objects.equals(current.state, state)) {
			return;
		}
		desired = new Activity(details, state, System.currentTimeMillis());
	}

	private static void run() {
		DiscordIpc ipc = null;
		Activity sent = null;
		long lastSend = 0;
		long nextConnect = 0;
		boolean loggedUnavailable = false;

		while (true) {
			try {
				Thread.sleep(POLL_MILLIS);
			} catch (InterruptedException e) {
				return;
			}

			Activity want = desired;
			long now = System.currentTimeMillis();
			if (want == null) {
				continue;
			}

			if (ipc == null) {
				if (now < nextConnect) {
					continue;
				}
				try {
					ipc = DiscordIpc.connect(CLIENT_ID);
					sent = null;
					loggedUnavailable = false;
					LOGGER.info("Connected to Discord");
				} catch (Exception e) {
					nextConnect = now + RECONNECT_DELAY_MILLIS;
					if (!loggedUnavailable) {
						loggedUnavailable = true;
						LOGGER.info("Discord not available, Rich Presence off until it starts ({})", e.getMessage());
					}
					continue;
				}
			}

			if (want.equals(sent) || now - lastSend < MIN_UPDATE_INTERVAL_MILLIS) {
				continue;
			}
			try {
				ipc.setActivity(toJson(want));
				sent = want;
				lastSend = now;
			} catch (Exception e) {
				LOGGER.info("Lost connection to Discord ({})", e.getMessage());
				ipc.close();
				ipc = null;
				nextConnect = now + RECONNECT_DELAY_MILLIS;
			}
		}
	}

	private static JsonObject toJson(Activity activity) {
		JsonObject json = new JsonObject();
		json.addProperty("details", activity.details);
		if (activity.state != null) {
			json.addProperty("state", activity.state);
		}

		JsonObject timestamps = new JsonObject();
		timestamps.addProperty("start", activity.startMillis);
		json.add("timestamps", timestamps);

		JsonObject assets = new JsonObject();
		assets.addProperty("large_image", LARGE_IMAGE);
		assets.addProperty("large_text", LARGE_TEXT);
		json.add("assets", assets);
		return json;
	}
}
