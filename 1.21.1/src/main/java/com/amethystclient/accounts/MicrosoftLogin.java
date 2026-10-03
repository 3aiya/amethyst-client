package com.amethystclient.accounts;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Microsoft account login: the browser sign-in (OAuth with PKCE, answered on a local port) gives a
 * refresh token, which is then exchanged Microsoft -> Xbox Live -> XSTS -> Minecraft for a game token.
 */
public final class MicrosoftLogin {
	private static final Logger LOGGER = LoggerFactory.getLogger("amethystclient/accounts");
	/** The Azure app the browser sign-in is for; override with -Damethystclient.msClientId=... */
	private static final String CLIENT_ID = System.getProperty("amethystclient.msClientId", "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb");
	private static final int PORT = 9675;
	private static final String REDIRECT_URI = "http://127.0.0.1:" + PORT;
	private static final String AUTHORIZE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
	private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
	private static final String SCOPE = "XboxLive.SignIn XboxLive.offline_access";

	// Checking many accounts at once would otherwise get rate limited.
	private static final Semaphore LOGIN_PERMITS = new Semaphore(2, true);
	private static final Object SPACING_LOCK = new Object();
	private static final long MIN_SPACING_MILLIS = 400;
	private static long lastLoginStart;

	private static HttpServer server;
	private static Consumer<String> callback;
	private static String codeVerifier = "";
	private static String state = "";

	/** The outcome of {@link #login}: either a game token for the profile, or an error. */
	public record Result(String accessToken, String refreshToken, String uuid, String username, long expiresAtMillis, String error) {
		static Result fail(String error) {
			return new Result(null, null, null, null, 0, error);
		}

		public boolean ok() {
			return accessToken != null;
		}
	}

	private MicrosoftLogin() {
	}

	/**
	 * Starts the browser sign-in and returns the page to open. {@code onRefreshToken} is called once
	 * with the refresh token, or with null if the sign-in failed or was declined.
	 */
	public static synchronized String startBrowserLogin(Consumer<String> onRefreshToken) throws IOException {
		stopBrowserLogin();
		HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
		created.createContext("/", MicrosoftLogin::handleRedirect);
		// The server's dispatcher thread inherits daemon status from the thread that starts it; a
		// forgotten login must not keep the game process alive after it closes.
		Thread starter = new Thread(created::start, "amethystclient-ms-login-start");
		starter.setDaemon(true);
		starter.start();
		try {
			starter.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		server = created;
		codeVerifier = randomToken();
		state = randomToken();
		callback = onRefreshToken;
		return AUTHORIZE_URL + "?client_id=" + CLIENT_ID
				+ "&response_type=code"
				+ "&redirect_uri=" + enc(REDIRECT_URI)
				+ "&scope=" + enc(SCOPE)
				+ "&code_challenge=" + enc(codeChallenge(codeVerifier))
				+ "&code_challenge_method=S256"
				+ "&state=" + enc(state)
				+ "&prompt=select_account";
	}

	/** Stops waiting for the browser; the pending callback is dropped. */
	public static synchronized void stopBrowserLogin() {
		if (server != null) {
			stopLater(server);
			server = null;
		}
		callback = null;
		state = "";
	}

	// stop() waits for a running handler (which may be mid token exchange), so not on the caller's thread.
	private static void stopLater(HttpServer stopping) {
		Thread thread = new Thread(() -> stopping.stop(0), "amethystclient-ms-login-stop");
		thread.setDaemon(true);
		thread.start();
	}

	private static void handleRedirect(HttpExchange exchange) throws IOException {
		// Browsers also ask for /favicon.ico and the like; only the redirect itself counts.
		if (!"GET".equals(exchange.getRequestMethod()) || !"/".equals(exchange.getRequestURI().getPath())) {
			respond(exchange, 404, "Not found");
			return;
		}
		Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
		Consumer<String> done;
		String verifier;
		HttpServer self;
		synchronized (MicrosoftLogin.class) {
			if (state.isEmpty() || !state.equals(query.get("state"))) {
				respond(exchange, 400, "This sign-in link has expired. Start the login again from Minecraft.");
				return;
			}
			done = callback;
			verifier = codeVerifier;
			self = server;
			// Answer only once; the server itself is stopped after the page has been sent.
			callback = null;
			state = "";
		}

		String code = query.get("code");
		String refreshToken = null;
		if (code != null) {
			Web.Reply reply = Web.postForm(TOKEN_URL, "client_id=" + CLIENT_ID
					+ "&code=" + enc(code)
					+ "&grant_type=authorization_code"
					+ "&redirect_uri=" + enc(REDIRECT_URI)
					+ "&scope=" + enc(SCOPE)
					+ "&code_verifier=" + enc(verifier));
			JsonObject json = reply.json();
			if (json != null && json.has("refresh_token")) {
				refreshToken = json.get("refresh_token").getAsString();
			} else {
				LOGGER.warn("Microsoft login: code exchange failed ({})", reply.detail());
			}
		}
		respond(exchange, 200, refreshToken != null
				? "Signed in. You can close this tab and go back to Minecraft."
				: "Sign-in didn't complete. You can close this tab and try again from Minecraft.");
		synchronized (MicrosoftLogin.class) {
			if (server == self && self != null) {
				stopLater(self);
				server = null;
			}
		}
		if (done != null) {
			done.accept(refreshToken);
		}
	}

	/** Turns a refresh token into a Minecraft game token. Blocks; call it off the render thread. */
	public static Result login(String refreshToken) {
		try {
			LOGIN_PERMITS.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Result.fail("Login interrupted");
		}
		try {
			synchronized (SPACING_LOCK) {
				long wait = lastLoginStart + MIN_SPACING_MILLIS - System.currentTimeMillis();
				if (wait > 0) {
					Thread.sleep(wait);
				}
				lastLoginStart = System.currentTimeMillis();
			}
			return doLogin(refreshToken);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Result.fail("Login interrupted");
		} finally {
			LOGIN_PERMITS.release();
		}
	}

	private static Result doLogin(String refreshToken) {
		Web.Reply tokenReply = Web.postForm(TOKEN_URL, "client_id=" + CLIENT_ID
				+ "&refresh_token=" + enc(refreshToken)
				+ "&grant_type=refresh_token"
				+ "&scope=" + enc(SCOPE));
		JsonObject token = tokenReply.json();
		if (token == null || !token.has("access_token") || !token.has("refresh_token")) {
			String reason = tokenReply.status() < 0 ? "Couldn't reach Microsoft (check your connection)" : "Microsoft: " + tokenReply.detail();
			LOGGER.warn("Microsoft login: token refresh failed ({})", tokenReply.detail());
			return Result.fail(reason);
		}
		String msAccessToken = token.get("access_token").getAsString();
		String newRefreshToken = token.get("refresh_token").getAsString();

		Web.Reply xblReply = Web.postJson("https://user.auth.xboxlive.com/user/authenticate",
				"{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d=" + msAccessToken
						+ "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}");
		JsonObject xbl = xblReply.json();
		if (!xblReply.ok() || xbl == null || !xbl.has("Token")) {
			return Result.fail("Xbox Live sign-in failed (" + xblReply.detail() + ")");
		}

		Web.Reply xstsReply = Web.postJson("https://xsts.auth.xboxlive.com/xsts/authorize",
				"{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xbl.get("Token").getAsString()
						+ "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}");
		JsonObject xsts = xstsReply.json();
		if (!xstsReply.ok() || xsts == null || !xsts.has("Token")) {
			return Result.fail(xstsError(xsts));
		}

		String userHash = userHash(xsts);
		if (userHash == null) {
			userHash = userHash(xbl);
		}
		if (userHash == null) {
			return Result.fail("Xbox Live answer had no user hash");
		}

		Web.Reply mcReply = Web.postJson("https://api.minecraftservices.com/authentication/login_with_xbox",
				"{\"identityToken\":\"XBL3.0 x=" + userHash + ";" + xsts.get("Token").getAsString() + "\"}");
		JsonObject mc = mcReply.json();
		if (!mcReply.ok() || mc == null || !mc.has("access_token")) {
			return Result.fail("Minecraft sign-in failed (" + mcReply.detail() + ")");
		}
		String mcToken = mc.get("access_token").getAsString();
		long expiresIn = mc.has("expires_in") ? mc.get("expires_in").getAsLong() : 86400;

		Web.Reply profileReply = Web.get("https://api.minecraftservices.com/minecraft/profile", mcToken);
		JsonObject profile = profileReply.json();
		if (profileReply.status() == 404) {
			return Result.fail("This Microsoft account doesn't own Minecraft: Java Edition");
		}
		if (profile == null || !profile.has("id") || !profile.has("name")) {
			return Result.fail("Couldn't read the Minecraft profile (" + profileReply.detail() + ")");
		}
		return new Result(mcToken, newRefreshToken, profile.get("id").getAsString(), profile.get("name").getAsString(),
				System.currentTimeMillis() + expiresIn * 1000, null);
	}

	private static String userHash(JsonObject xbox) {
		try {
			return xbox.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
		} catch (Exception e) {
			return null;
		}
	}

	private static String xstsError(JsonObject xsts) {
		if (xsts != null && xsts.has("XErr")) {
			long code = xsts.get("XErr").getAsLong();
			if (code == 2148916233L) {
				return "This account has no Xbox profile - create one at xbox.com first";
			}
			if (code == 2148916235L) {
				return "Xbox Live isn't available in this account's country";
			}
			if (code == 2148916236L || code == 2148916237L) {
				return "This account needs adult verification";
			}
			if (code == 2148916238L) {
				return "Child account - an adult must add it to a Microsoft family";
			}
		}
		return "Xbox authorization failed";
	}

	private static void respond(HttpExchange exchange, int status, String text) throws IOException {
		byte[] body = ("<!doctype html><meta charset=utf-8><title>Amethyst Client</title>"
				+ "<body style=\"font-family:sans-serif;background:#1b1622;color:#eee;text-align:center;padding-top:15vh\">"
				+ "<h2>Amethyst Client</h2><p>" + text + "</p>").getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
		exchange.sendResponseHeaders(status, body.length);
		try (OutputStream output = exchange.getResponseBody()) {
			output.write(body);
		}
	}

	private static Map<String, String> parseQuery(String raw) {
		Map<String, String> query = new HashMap<>();
		if (raw == null) {
			return query;
		}
		for (String pair : raw.split("&")) {
			int eq = pair.indexOf('=');
			String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
			String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			if (!name.isEmpty()) {
				query.put(name, value);
			}
		}
		return query;
	}

	private static String randomToken() {
		byte[] bytes = new byte[48];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String codeChallenge(String verifier) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	private static String enc(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}
}
