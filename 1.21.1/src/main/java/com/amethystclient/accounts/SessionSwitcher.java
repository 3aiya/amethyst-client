package com.amethystclient.accounts;

import com.amethystclient.mixin.MinecraftAccessor;
import com.mojang.authlib.Environment;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.session.report.ReporterEnvironment;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Makes another account the game's current one, the way the game sets up its own account at launch. */
public final class SessionSwitcher {
	private static final Logger LOGGER = LoggerFactory.getLogger("amethystclient/accounts");
	private static final Environment ALTENING = new Environment(AlteningLogin.SESSION_HOST, AlteningLogin.AUTH_HOST, "TheAltening");

	private static Session originalSession;
	private static volatile String lastError = "";

	private SessionSwitcher() {
	}

	/** The account the game was launched with. */
	public static synchronized Session originalUser() {
		if (originalSession == null) {
			originalSession = MinecraftClient.getInstance().getSession();
		}
		return originalSession;
	}

	public static String lastError() {
		return lastError;
	}

	/** Switches back to the launcher's account. */
	public static boolean restoreOriginal() {
		Session session = originalUser();
		return apply(session, new YggdrasilAuthenticationService(MinecraftClient.getInstance().getNetworkProxy()));
	}

	/** {@code altening}: join servers through TheAltening's session server instead of Mojang's. */
	static boolean switchTo(String name, UUID uuid, String accessToken, boolean altening) {
		originalUser();
		MinecraftClient mc = MinecraftClient.getInstance();
		Session.AccountType type = accessToken.isBlank() ? Session.AccountType.LEGACY : Session.AccountType.MSA;
		Session session = new Session(name, uuid, accessToken, Optional.empty(), Optional.empty(), type);
		return apply(session, altening ? new YggdrasilAuthenticationService(mc.getNetworkProxy(), ALTENING) : new YggdrasilAuthenticationService(mc.getNetworkProxy()));
	}

	private static boolean apply(Session session, YggdrasilAuthenticationService authService) {
		lastError = "";
		MinecraftClient mc = MinecraftClient.getInstance();
		try {
			// Build everything first (this can touch the network), then swap it in on the client thread.
			YggdrasilAuthenticationService mojang = new YggdrasilAuthenticationService(mc.getNetworkProxy());
			String token = session.getAccessToken();
			MinecraftSessionService sessionService = authService.createMinecraftSessionService();
			UserApiService userApi = token.isBlank() ? UserApiService.OFFLINE : mojang.createUserApiService(token);
			SocialInteractionsManager socialManager = new SocialInteractionsManager(mc, userApi);
			ProfileKeys keys = ProfileKeys.create(userApi, session, mc.runDirectory.toPath());
			PlayerSkinProvider skins = new PlayerSkinProvider(mc.getTextureManager(), mc.runDirectory.toPath().resolve("assets").resolve("skins"), sessionService, mc);

			Runnable swap = () -> {
				MinecraftAccessor accessor = (MinecraftAccessor) mc;
				accessor.amethystclient$setSession(session);
				accessor.amethystclient$setAuthenticationService(authService);
				accessor.amethystclient$setSessionService(sessionService);
				accessor.amethystclient$setUserApiService(userApi);
				accessor.amethystclient$setUserPropertiesFuture(CompletableFuture.supplyAsync(() -> {
					try {
						return userApi.fetchProperties();
					} catch (AuthenticationException e) {
						return UserApiService.OFFLINE_PROPERTIES;
					}
				}, Util.getIoWorkerExecutor()));
				accessor.amethystclient$setGameProfileFuture(CompletableFuture.supplyAsync(
						() -> sessionService.fetchProfile(session.getUuidOrNull(), true), Util.getIoWorkerExecutor()));
				accessor.amethystclient$setProfileKeys(keys);
				accessor.amethystclient$setSkinProvider(skins);
				accessor.amethystclient$setSocialInteractionsManager(socialManager);
				accessor.amethystclient$setAbuseReportContext(AbuseReportContext.create(ReporterEnvironment.ofIntegratedServer(), userApi));
			};
			if (mc.isOnThread()) {
				swap.run();
			} else {
				mc.submit(swap).join();
			}
			return true;
		} catch (Exception e) {
			LOGGER.error("Couldn't switch to account {}", session.getUsername(), e);
			String message = e.getMessage();
			lastError = message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
			return false;
		}
	}
}
