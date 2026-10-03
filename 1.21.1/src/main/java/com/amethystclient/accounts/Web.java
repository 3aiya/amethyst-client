package com.amethystclient.accounts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** The small HTTP client the account code uses for Microsoft, Mojang and TheAltening requests. */
final class Web {
	private static final Duration TIMEOUT = Duration.ofSeconds(20);
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	/** A response; {@code status} is -1 when the request didn't get an answer at all. */
	record Reply(int status, String body) {
		boolean ok() {
			return status >= 200 && status < 300;
		}

		JsonObject json() {
			try {
				return body == null || body.isBlank() ? null : JsonParser.parseString(body).getAsJsonObject();
			} catch (Exception e) {
				return null;
			}
		}

		/** "HTTP 401: message" from the usual error fields, for showing to the player. */
		String detail() {
			JsonObject json = json();
			if (json != null) {
				for (String key : new String[] {"error_description", "errorMessage", "message", "error"}) {
					try {
						if (json.has(key) && !json.get(key).getAsString().isBlank()) {
							return "HTTP " + status + ": " + json.get(key).getAsString().split("\\r?\\n")[0].trim();
						}
					} catch (Exception ignored) {
						// Not a string; try the next field.
					}
				}
			}
			return status < 0 ? "no response (check your connection)" : "HTTP " + status;
		}
	}

	private Web() {
	}

	static Reply get(String url, String bearer) {
		HttpRequest.Builder request = base(url).GET();
		if (bearer != null && !bearer.isBlank()) {
			request.header("Authorization", "Bearer " + bearer);
		}
		return send(request.build());
	}

	static Reply postJson(String url, String json) {
		return send(base(url).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build());
	}

	static Reply postForm(String url, String form) {
		return send(base(url).header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)).build());
	}

	private static HttpRequest.Builder base(String url) {
		return HttpRequest.newBuilder(URI.create(url))
				.timeout(TIMEOUT)
				.header("Accept", "application/json");
	}

	private static Reply send(HttpRequest request) {
		try {
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return new Reply(response.statusCode(), response.body());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Reply(-1, null);
		} catch (Exception e) {
			return new Reply(-1, null);
		}
	}
}
