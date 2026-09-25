package com.amethystclient.mixin;

import com.amethystclient.packcache.CachedPackProvider;
import net.minecraft.client.resource.DefaultClientResourcePackProvider;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registers {@link CachedPackProvider} on the client's resource pack manager. This is how the
 * cached pack gets into the resource system at game startup, before any server connection exists.
 */
@Mixin(ResourcePackManager.class)
public abstract class ResourcePackManagerMixin {
	@Shadow
	@Final
	@Mutable
	private Set<ResourcePackProvider> providers;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void amethystclient$addCachedPackProvider(ResourcePackProvider[] packProviders, CallbackInfo ci) {
		// ResourcePackManager is also used for data packs (singleplayer worlds). Only the client resource
		// manager contains the default client provider, so that's the one to extend.
		boolean isClientResourceManager = false;
		for (ResourcePackProvider provider : packProviders) {
			if (provider instanceof DefaultClientResourcePackProvider) {
				isClientResourceManager = true;
				break;
			}
		}
		if (!isClientResourceManager) {
			return;
		}

		// The original set is immutable; replace it with a copy that includes our provider.
		Set<ResourcePackProvider> withCache = new LinkedHashSet<>(this.providers);
		withCache.add(new CachedPackProvider());
		this.providers = withCache;
	}
}
