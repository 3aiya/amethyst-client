package com.amethystclient.accounts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.util.UndashedUuid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** Mojang profile lookups, used to show skins in the account list. */
public final class MojangProfiles {
	/** A profile with its signed "textures" property, ready to hand to the skin manager. */
	public record Textured(UUID id, String name, String textures, String signature) {
	}

	private MojangProfiles() {
	}

	/** Looks up a premium profile by name, so a cracked account named like a real player shows that skin. */
	public static Optional<Textured> byName(String name) {
		if (!AccountGenerator.isValidName(name)) {
			return Optional.empty();
		}
		JsonObject json = Web.get("https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(name, StandardCharsets.UTF_8), null).json();
		if (json == null || !json.has("id")) {
			return Optional.empty();
		}
		return byId(UndashedUuid.fromStringLenient(json.get("id").getAsString()));
	}

	public static Optional<Textured> byId(UUID id) {
		JsonObject json = Web.get("https://sessionserver.mojang.com/session/minecraft/profile/" + UndashedUuid.toString(id) + "?unsigned=false", null).json();
		if (json == null || !json.has("properties") || !json.get("properties").isJsonArray()) {
			return Optional.empty();
		}
		String name = json.has("name") ? json.get("name").getAsString() : "";
		for (JsonElement element : json.getAsJsonArray("properties")) {
			JsonObject property = element.getAsJsonObject();
			if ("textures".equals(string(property, "name")) && !string(property, "value").isEmpty()) {
				return Optional.of(new Textured(id, name, string(property, "value"), string(property, "signature")));
			}
		}
		return Optional.empty();
	}

	private static String string(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
	}
}
