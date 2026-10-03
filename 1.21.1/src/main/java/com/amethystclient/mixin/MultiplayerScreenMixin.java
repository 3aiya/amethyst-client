package com.amethystclient.mixin;

import com.amethystclient.AmethystServers;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pinned Amethyst server can only be joined: Edit and Delete stay greyed out for it. */
@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin {
	@Shadow
	protected MultiplayerServerListWidget serverListWidget;

	@Shadow
	private ServerList serverList;

	@Shadow
	private ButtonWidget buttonEdit;

	@Shadow
	private ButtonWidget buttonDelete;

	@Inject(method = "updateButtonActivationStates", at = @At("TAIL"))
	private void amethystclient$lockPinnedServer(CallbackInfo ci) {
		if (serverListWidget.getSelectedOrNull() instanceof MultiplayerServerListWidget.ServerEntry entry
				&& AmethystServers.isPinned(serverList, entry.getServer())) {
			buttonEdit.active = false;
			buttonDelete.active = false;
		}
	}
}
