package com.amethystclient.accounts;

import com.google.gson.JsonObject;

/**
 * TheAltening alts: the alt token is the "username" of a Yggdrasil login on TheAltening's auth
 * server, which answers with a game token for the alt's profile. Joining servers with it then goes
 * through TheAltening's session server (see {@link SessionSwitcher}).
 */
final class AlteningLogin {
	static final String SESSION_HOST = "http://sessionserver.thealtening.com";
	static final String AUTH_HOST = "http://authserver.thealtening.com";
	// TheAltening ignores the password; any non-empty value works.
	private static final String PASSWORD = "Amethyst Client";

	record Result(String accessToken, String uuid, String username, String error) {
		boolean ok() {
			return accessToken != null;
		}
	}

	private AlteningLogin() {
	}

	static Result login(String alteningToken) {
		JsonObject body = new JsonObject();
		JsonObject agent = new JsonObject();
		agent.addProperty("name", "Minecraft");
		agent.addProperty("version", 1);
		body.add("agent", agent);
		body.addProperty("username", alteningToken);
		body.addProperty("password", PASSWORD);
		body.addProperty("requestUser", true);

		Web.Reply reply = Web.postJson(AUTH_HOST + "/authenticate", body.toString());
		JsonObject json = reply.json();
		if (!reply.ok() || json == null || !json.has("accessToken") || !json.has("selectedProfile")) {
			return new Result(null, null, null, reply.status() == 403 ? "Invalid or expired TheAltening token" : "TheAltening: " + reply.detail());
		}
		JsonObject profile = json.getAsJsonObject("selectedProfile");
		return new Result(json.get("accessToken").getAsString(), profile.get("id").getAsString(), profile.get("name").getAsString(), null);
	}
}
