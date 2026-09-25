package com.amethystclient.packcache;

import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackPosition;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.ZipResourcePack;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.function.Consumer;

import static com.amethystclient.packcache.ServerPackManager.LOGGER;

/**
 * Adds the active cached pack to the client's resource pack list. It is registered on the client
 * {@code ResourcePackManager} by {@code ResourcePackManagerMixin}.
 *
 * <p>The game calls {@link #register} every time it rescans packs, which it does at the start of
 * every resource reload, including the first load at game startup. So applying a different pack
 * only takes two steps: change {@link ServerPackManager#getActivePack()}, then call
 * {@code MinecraftClient.reloadResources()}.
 */
public final class CachedPackProvider implements ResourcePackProvider {
	/**
	 * Same position vanilla uses for server packs:
	 * - required: always enabled, without being listed in options.txt.
	 * - TOP: overrides the player's own resource packs, as a server pack normally does.
	 * - fixed: pinned in the pack screen and never saved to options.txt.
	 */
	private static final ResourcePackPosition POSITION = new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.TOP, true);

	@Override
	public void register(Consumer<ResourcePackProfile> profileAdder) {
		PackCache.CachedPack pack = ServerPackManager.get().getActivePack();
		if (pack == null) {
			return;
		}

		ResourcePackInfo info = new ResourcePackInfo(
				"amethystclient/cached/" + pack.serverKey(),
				Text.literal("Cached server pack (" + pack.serverKey() + ")"),
				ResourcePackSource.SERVER,
				Optional.empty()
		);
		ResourcePackProfile profile = ResourcePackProfile.create(info, new ZipResourcePack.ZipBackedFactory(pack.zip()), ResourceType.CLIENT_RESOURCES, POSITION);
		if (profile == null) {
			// Metadata could not be read (e.g. the file vanished after verification). Skip it; the next join re-checks.
			LOGGER.warn("[cache] Could not read pack metadata of cached pack {}; it will not be applied", pack.zip());
			return;
		}
		profileAdder.accept(profile);
	}
}
