package com.amethystclient.mixin;

import com.amethystclient.AmethystServers;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pinned Amethyst server can only be joined: Edit and Delete stay greyed out for it. */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin {
	@Shadow
	protected ServerSelectionList serverSelectionList;

	@Shadow
	private ServerList servers;

	@Shadow
	private Button editButton;

	@Shadow
	private Button deleteButton;

	@Inject(method = "onSelectedChange", at = @At("TAIL"))
	private void amethystclient$lockPinnedServer(CallbackInfo ci) {
		if (serverSelectionList.getSelected() instanceof ServerSelectionList.OnlineServerEntry entry
				&& AmethystServers.isPinned(servers, entry.getServerData())) {
			editButton.active = false;
			deleteButton.active = false;
		}
	}
}
