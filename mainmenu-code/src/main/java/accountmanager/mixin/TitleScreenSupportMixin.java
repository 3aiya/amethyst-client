package accountmanager.mixin;

import accountmanager.gui.screen.ModuleScreen;
import accountmanager.gui.screen.OverlayHostScreen;
import accountmanager.modules.ClientModule;
import accountmanager.modules.PackHideState;
import accountmanager.util.Links;
import accountmanager.util.MatchmakingOverlay;
import accountmanager.util.OverlayManager;
import accountmanager.util.ProfilesOverlay;
import accountmanager.util.IOverlay;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenSupportMixin extends Screen {
    protected TitleScreenSupportMixin() {
        super(null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void acc$addSupportButtons(CallbackInfo ci) {
        if (PackHideState.isActive()) return;

        acc$rightButton(Component.literal("Modules & Macros"), 4, b -> {
            if (!PackHideState.isHardLocked()) {
                this.minecraft.gui.setScreen(new ModuleScreen(this, ModuleScreen.Mode.TITLE_SETUP));
            }
        });
        acc$rightButton(Component.literal("Matchmaking"), 28, b -> acc$openMenuOverlay(true));
        acc$rightButton(Component.literal("Profiles"), 52, b -> acc$openMenuOverlay(false));

        acc$leftButton(Component.literal("Card/PayPal"), 4, Links.KOFI);
        acc$leftButton(Component.literal("Crypto"), 28, Links.CRYPTO_DONATE);
        acc$leftButton(Component.literal("Community"), 52, Links.COMMUNITY_DISCORD);
        acc$leftButton(Component.literal("Client"), 76, Links.DISCORD);
    }

    @Unique
    private void acc$rightButton(Component label, int y, Button.OnPress onPress) {
        int w = acc$buttonWidth(label);
        this.addRenderableWidget(Button.builder(label, onPress).bounds(this.width - w - 4, y, w, 20).build());
    }

    @Unique
    private void acc$leftButton(Component label, int y, String url) {
        this.addRenderableWidget(Button.builder(label, b -> Links.open(url))
            .bounds(4, y, acc$buttonWidth(label), 20).build());
    }

    @Unique
    private int acc$buttonWidth(Component label) {
        return Math.max(96, Math.min(this.width - 8, this.font.width(label) + 20));
    }

    @Unique
    private void acc$openMenuOverlay(boolean matchmaking) {
        if (PackHideState.isHardLocked()) return;
        ClientModule mod = ClientModule.get();
        if (mod == null) return;
        IOverlay overlay = matchmaking ? mod.getMatchmakingOverlay() : mod.getProfilesOverlay();
        if (overlay == null) return;
        OverlayManager.get().register(overlay);
        if (overlay instanceof MatchmakingOverlay mm) mm.setMainMenuMode(true);
        else if (overlay instanceof ProfilesOverlay pf) pf.setMainMenuMode(true);
        overlay.setVisible(true);
        this.minecraft.gui.setScreen(new OverlayHostScreen(overlay, this, true));
    }
}
