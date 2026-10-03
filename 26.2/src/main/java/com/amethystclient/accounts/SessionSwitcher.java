package com.amethystclient.accounts;

import com.amethystclient.mixin.MinecraftAccessor;
import com.mojang.authlib.Environment;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Makes another account the game's current one, the way the game sets up its own account at launch. */
public final class SessionSwitcher {
	private static final Logger LOGGER = LoggerFactory.getLogger("amethystclient/accounts");
	private static final Environment ALTENING = new Environment(AlteningLogin.SESSION_HOST, AlteningLogin.AUTH_HOST, "https://api.mojang.com", "TheAltening");

	private static User originalUser;
	private static volatile String lastError = "";

	private SessionSwitcher() {
	}

	/** The account the game was launched with. */
	public static synchronized User originalUser() {
		if (originalUser == null) {
			originalUser = Minecraft.getInstance().getUser();
		}
		return originalUser;
	}

	public static String lastError() {
		return lastError;
	}

	/** Switches back to the launcher's account. */
	public static boolean restoreOriginal() {
		User user = originalUser();
		return apply(user, new YggdrasilAuthenticationService(Minecraft.getInstance().getProxy()));
	}

	/** {@code altening}: join servers through TheAltening's session server instead of Mojang's. */
	static boolean switchTo(String name, UUID uuid, String accessToken, boolean altening) {
		originalUser();
		Minecraft mc = Minecraft.getInstance();
		User user = new User(name, uuid, accessToken, Optional.empty(), Optional.empty());
		return apply(user, altening ? new YggdrasilAuthenticationService(mc.getProxy(), ALTENING) : new YggdrasilAuthenticationService(mc.getProxy()));
	}

	private static boolean apply(User user, YggdrasilAuthenticationService authService) {
		lastError = "";
		Minecraft mc = Minecraft.getInstance();
		try {
			// Build everything first (this can touch the network), then swap it in on the client thread.
			YggdrasilAuthenticationService mojang = new YggdrasilAuthenticationService(mc.getProxy());
			String token = user.getAccessToken();
			Services services = Services.create(authService, mc.gameDirectory);
			UserApiService userApi = token.isBlank() ? UserApiService.OFFLINE : mojang.createUserApiService(token);
			FriendsService friends = mojang.createFriendsService(token);
			RemoteFriendListUpdateHandler friendUpdates = new RemoteFriendListUpdateHandler(friends, mc);
			PlayerSocialManager socialManager = new PlayerSocialManager(mc, userApi, friends, friendUpdates);
			ProfileKeyPairManager keys = ProfileKeyPairManager.create(userApi, user, mc.gameDirectory.toPath());
			SkinManager skins = new SkinManager(mc.gameDirectory.toPath().resolve("assets").resolve("skins"), services,
					new SkinTextureDownloader(mc.getProxy(), mc.getTextureManager(), mc), mc);

			Runnable swap = () -> {
				MinecraftAccessor accessor = (MinecraftAccessor) mc;
				RemoteFriendListUpdateHandler previous = accessor.amethystclient$getRemoteFriendListUpdateHandler();
				if (previous != null) {
					previous.close();
				}
				accessor.amethystclient$setUser(user);
				accessor.amethystclient$setServices(services);
				accessor.amethystclient$setUserApiService(userApi);
				accessor.amethystclient$setUserPropertiesFuture(CompletableFuture.supplyAsync(() -> {
					try {
						return userApi.fetchProperties();
					} catch (AuthenticationException e) {
						return UserApiService.OFFLINE_PROPERTIES;
					}
				}, Util.nonCriticalIoPool()));
				accessor.amethystclient$setProfileFuture(CompletableFuture.supplyAsync(
						() -> services.sessionService().fetchProfile(user.getProfileId(), true), Util.nonCriticalIoPool()));
				accessor.amethystclient$setProfileKeyPairManager(keys);
				accessor.amethystclient$setSkinManager(skins);
				accessor.amethystclient$setRemoteFriendListUpdateHandler(friendUpdates);
				accessor.amethystclient$setPlayerSocialManager(socialManager);
				accessor.amethystclient$setReportingContext(ReportingContext.create(ReportEnvironment.local(), userApi));
				if (socialManager.isFriendListEnabled()) {
					friendUpdates.start();
				}
			};
			if (mc.isSameThread()) {
				swap.run();
			} else {
				mc.submit(swap).join();
			}
			return true;
		} catch (Exception e) {
			LOGGER.error("Couldn't switch to account {}", user.getName(), e);
			String message = e.getMessage();
			lastError = message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
			return false;
		}
	}
}
