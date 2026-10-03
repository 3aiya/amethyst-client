package com.amethystclient.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The account-bound parts of the client, replaced when switching accounts (see SessionSwitcher). */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Mutable
	@Accessor("user")
	void amethystclient$setUser(User user);

	@Mutable
	@Accessor("services")
	void amethystclient$setServices(Services services);

	@Mutable
	@Accessor("userApiService")
	void amethystclient$setUserApiService(UserApiService service);

	@Mutable
	@Accessor("userPropertiesFuture")
	void amethystclient$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> future);

	@Mutable
	@Accessor("profileFuture")
	void amethystclient$setProfileFuture(CompletableFuture<ProfileResult> future);

	@Mutable
	@Accessor("profileKeyPairManager")
	void amethystclient$setProfileKeyPairManager(ProfileKeyPairManager manager);

	@Mutable
	@Accessor("skinManager")
	void amethystclient$setSkinManager(SkinManager manager);

	@Mutable
	@Accessor("playerSocialManager")
	void amethystclient$setPlayerSocialManager(PlayerSocialManager manager);

	@Accessor("remoteFriendListUpdateHandler")
	RemoteFriendListUpdateHandler amethystclient$getRemoteFriendListUpdateHandler();

	@Mutable
	@Accessor("remoteFriendListUpdateHandler")
	void amethystclient$setRemoteFriendListUpdateHandler(RemoteFriendListUpdateHandler handler);

	@Accessor("reportingContext")
	void amethystclient$setReportingContext(ReportingContext context);
}
