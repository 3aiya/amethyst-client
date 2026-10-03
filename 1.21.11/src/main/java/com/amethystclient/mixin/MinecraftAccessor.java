package com.amethystclient.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.util.ApiServices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The account-bound parts of the client, replaced when switching accounts (see SessionSwitcher). */
@Mixin(MinecraftClient.class)
public interface MinecraftAccessor {
	@Mutable
	@Accessor("session")
	void amethystclient$setSession(Session session);

	@Mutable
	@Accessor("apiServices")
	void amethystclient$setApiServices(ApiServices services);

	@Mutable
	@Accessor("userApiService")
	void amethystclient$setUserApiService(UserApiService service);

	@Mutable
	@Accessor("userPropertiesFuture")
	void amethystclient$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> future);

	@Mutable
	@Accessor("gameProfileFuture")
	void amethystclient$setGameProfileFuture(CompletableFuture<ProfileResult> future);

	@Mutable
	@Accessor("profileKeys")
	void amethystclient$setProfileKeys(ProfileKeys keys);

	@Mutable
	@Accessor("skinProvider")
	void amethystclient$setSkinProvider(PlayerSkinProvider provider);

	@Mutable
	@Accessor("socialInteractionsManager")
	void amethystclient$setSocialInteractionsManager(SocialInteractionsManager manager);

	@Accessor("abuseReportContext")
	void amethystclient$setAbuseReportContext(AbuseReportContext context);
}
