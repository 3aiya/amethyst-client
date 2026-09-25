package com.amethystclient.mixin;

import com.amethystclient.packcache.CachedPackProvider;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
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
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
	@Shadow
	@Final
	@Mutable
	private Set<RepositorySource> sources;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void amethystclient$addCachedPackProvider(RepositorySource[] packSources, CallbackInfo ci) {
		// PackRepository is also used for data packs (singleplayer worlds). Only the client resource
		// manager contains the default client provider, so that's the one to extend.
		boolean isClientResourceManager = false;
		for (RepositorySource source : packSources) {
			if (source instanceof ClientPackSource) {
				isClientResourceManager = true;
				break;
			}
		}
		if (!isClientResourceManager) {
			return;
		}

		// The original set is immutable; replace it with a copy that includes our provider.
		Set<RepositorySource> withCache = new LinkedHashSet<>(this.sources);
		withCache.add(new CachedPackProvider());
		this.sources = withCache;
	}
}
