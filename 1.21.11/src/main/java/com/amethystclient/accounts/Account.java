package com.amethystclient.accounts;

import com.google.gson.JsonObject;
import com.mojang.util.UndashedUuid;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** One saved account. Its network work ({@link #refresh}, {@link #login}) blocks; run it off the render thread. */
public final class Account {
	/** The result of the last "Check" of this account; not saved. */
	public enum Status { UNKNOWN, CHECKING, VALID, EXPIRED }

	// Refresh a Microsoft game token this long before it runs out.
	private static final long REFRESH_MARGIN_MILLIS = 5 * 60 * 1000;

	public final String id;
	public final AccountType type;
	private volatile String username = "";
	private volatile String uuid = "";
	/** Microsoft: the long-lived token the game token is renewed with. */
	private volatile String refreshToken = "";
	/** TheAltening: the alt token. */
	private volatile String alteningToken = "";
	/** Microsoft, Session and TheAltening: the game token used to join servers. */
	private volatile String accessToken = "";
	private volatile long accessTokenExpiresAt;

	public volatile Status status = Status.UNKNOWN;
	private volatile String lastError = "";

	private Account(String id, AccountType type) {
		this.id = id;
		this.type = type;
	}

	public static Account cracked(String username, AccountType type) {
		Account account = new Account(UUID.randomUUID().toString(), type);
		account.username = username.trim();
		account.uuid = offlineUuid(account.username).toString();
		return account;
	}

	public static Account microsoft(String refreshToken) {
		Account account = new Account(UUID.randomUUID().toString(), AccountType.MICROSOFT);
		account.refreshToken = refreshToken;
		return account;
	}

	public static Account session(String accessToken) {
		Account account = new Account(UUID.randomUUID().toString(), AccountType.SESSION);
		account.accessToken = accessToken.trim();
		return account;
	}

	public static Account theAltening(String alteningToken) {
		Account account = new Account(UUID.randomUUID().toString(), AccountType.THE_ALTENING);
		account.alteningToken = alteningToken.trim();
		return account;
	}

	/** The UUID offline-mode servers give a player with this name. */
	public static UUID offlineUuid(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}

	public String username() {
		return username;
	}

	public String displayName() {
		return username.isBlank() ? "(unknown " + type.label + " account)" : username;
	}

	public UUID uuid() {
		return parseUuid(uuid);
	}

	public String lastError() {
		return lastError;
	}

	/** The game token, which can be shared as a Session account; empty for cracked accounts. */
	public String gameToken() {
		return accessToken;
	}

	/**
	 * Fetches the account's current name, UUID and (if needed) a fresh game token. Returns false and
	 * sets {@link #lastError} when the account no longer works.
	 */
	public boolean refresh() {
		lastError = "";
		switch (type) {
			case CRACKED, GENERATED -> {
				if (username.isBlank()) {
					return fail("missing username");
				}
				uuid = offlineUuid(username).toString();
				return true;
			}
			case SESSION -> {
				if (accessToken.isBlank()) {
					return fail("missing access token");
				}
				Web.Reply reply = Web.get("https://api.minecraftservices.com/minecraft/profile", accessToken);
				JsonObject profile = reply.json();
				if (reply.status() == 401) {
					return fail("token expired");
				}
				if (!reply.ok() || profile == null || !profile.has("id") || !profile.has("name")) {
					return fail(reply.status() == 404 ? "no Minecraft profile on this token" : reply.detail());
				}
				uuid = profile.get("id").getAsString();
				username = profile.get("name").getAsString();
				return true;
			}
			case MICROSOFT -> {
				if (hasFreshGameToken()) {
					return true;
				}
				if (refreshToken.isBlank()) {
					return fail("missing refresh token - log in with Microsoft again");
				}
				MicrosoftLogin.Result result = MicrosoftLogin.login(refreshToken);
				if (!result.ok()) {
					return fail(result.error());
				}
				accessToken = result.accessToken();
				refreshToken = result.refreshToken();
				uuid = result.uuid();
				username = result.username();
				accessTokenExpiresAt = result.expiresAtMillis();
				return true;
			}
			case THE_ALTENING -> {
				if (alteningToken.isBlank()) {
					return fail("missing TheAltening token");
				}
				AlteningLogin.Result result = AlteningLogin.login(alteningToken);
				if (!result.ok()) {
					return fail(result.error());
				}
				accessToken = result.accessToken();
				uuid = result.uuid();
				username = result.username();
				return true;
			}
		}
		return fail("unknown account type");
	}

	/** Refreshes the account if needed and makes it the game's current account. */
	public boolean login() {
		lastError = "";
		boolean needsRefresh = username.isBlank()
				|| type == AccountType.MICROSOFT && !hasFreshGameToken()
				|| type == AccountType.THE_ALTENING;
		if (needsRefresh && !refresh()) {
			return false;
		}
		UUID profileId = uuid();
		if (profileId == null) {
			return fail("missing UUID");
		}
		String token = type.isCracked() ? "" : accessToken;
		if (!type.isCracked() && token.isBlank()) {
			return fail("missing access token");
		}
		if (!SessionSwitcher.switchTo(username, profileId, token, type == AccountType.THE_ALTENING)) {
			return fail(SessionSwitcher.lastError());
		}
		return true;
	}

	/** Microsoft: the next login will renew the game token instead of trusting the saved one. */
	public void forgetGameToken() {
		if (type == AccountType.MICROSOFT) {
			accessToken = "";
			accessTokenExpiresAt = 0;
		}
	}

	/** Microsoft: replaces the refresh token after the player signed in with the browser again. */
	public void setRefreshToken(String token) {
		refreshToken = token;
		forgetGameToken();
	}

	/** Takes over the tokens of {@code fresh}, a new sign-in to this same Microsoft account. */
	public void adoptSignIn(Account fresh) {
		refreshToken = fresh.refreshToken;
		accessToken = fresh.accessToken;
		accessTokenExpiresAt = fresh.accessTokenExpiresAt;
		username = fresh.username;
		uuid = fresh.uuid;
	}

	/** Cracked accounts can be renamed in place; their UUID follows the name. */
	void rename(String newName) {
		username = newName.trim();
		uuid = offlineUuid(username).toString();
	}

	private boolean hasFreshGameToken() {
		return !accessToken.isBlank() && !username.isBlank() && !uuid.isBlank()
				&& accessTokenExpiresAt > System.currentTimeMillis() + REFRESH_MARGIN_MILLIS;
	}

	private boolean fail(String error) {
		lastError = error == null || error.isBlank() ? "unknown error" : error;
		return false;
	}

	/** Whether this is the same account as {@code other}, used to refuse duplicates. */
	public boolean sameAs(Account other) {
		if (other == null || other.type.isCracked() != type.isCracked()) {
			return false;
		}
		if (type.isCracked()) {
			return username.equalsIgnoreCase(other.username);
		}
		if (type != other.type) {
			return false;
		}
		UUID mine = uuid();
		if (mine != null && mine.equals(other.uuid())) {
			return true;
		}
		return switch (type) {
			case SESSION -> !accessToken.isBlank() && accessToken.equals(other.accessToken);
			case THE_ALTENING -> !alteningToken.isBlank() && alteningToken.equals(other.alteningToken);
			default -> false;
		};
	}

	/** Key used to remember which accounts were already imported from another client. */
	String importKey() {
		UUID id = uuid();
		String identity = id != null ? id.toString() : type.isCracked() ? username.toLowerCase(Locale.ROOT) : alteningToken + accessToken + refreshToken;
		return (type.isCracked() ? "cracked" : type.name()) + "|" + identity;
	}

	JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("id", id);
		json.addProperty("type", type.name());
		json.addProperty("username", username);
		json.addProperty("uuid", uuid);
		json.addProperty("refreshToken", Secrets.seal(refreshToken));
		json.addProperty("alteningToken", Secrets.seal(alteningToken));
		json.addProperty("accessToken", Secrets.seal(accessToken));
		json.addProperty("accessTokenExpiresAt", accessTokenExpiresAt);
		return json;
	}

	static Account fromJson(JsonObject json) {
		AccountType type;
		try {
			type = AccountType.valueOf(string(json, "type"));
		} catch (IllegalArgumentException e) {
			return null;
		}
		String id = string(json, "id");
		Account account = new Account(id.isBlank() ? UUID.randomUUID().toString() : id, type);
		account.username = string(json, "username");
		account.uuid = string(json, "uuid");
		account.refreshToken = Secrets.open(string(json, "refreshToken"));
		account.alteningToken = Secrets.open(string(json, "alteningToken"));
		account.accessToken = Secrets.open(string(json, "accessToken"));
		account.accessTokenExpiresAt = json.has("accessTokenExpiresAt") ? json.get("accessTokenExpiresAt").getAsLong() : 0;
		return account;
	}

	/** For importing another client's account, whose fields are already known. */
	static Account imported(AccountType type, String username, String uuid, String token) {
		Account account = new Account(UUID.randomUUID().toString(), type);
		account.username = username == null ? "" : username;
		account.uuid = uuid == null ? "" : uuid;
		switch (type) {
			case MICROSOFT -> account.refreshToken = token;
			case SESSION -> account.accessToken = token;
			case THE_ALTENING -> account.alteningToken = token;
			default -> account.uuid = offlineUuid(account.username).toString();
		}
		return account;
	}

	private static String string(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
	}

	private static UUID parseUuid(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return value.contains("-") ? UUID.fromString(value) : UndashedUuid.fromStringLenient(value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
