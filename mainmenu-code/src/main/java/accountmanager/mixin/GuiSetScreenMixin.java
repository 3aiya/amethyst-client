package accountmanager.mixin;

import accountmanager.gui.screen.PauseScreen;
import accountmanager.gui.screen.PanicTitleScreen;
import accountmanager.gui.screen.TitleScreen;
import accountmanager.gui.screen.WelcomeScreen;
import accountmanager.modules.PackHideState;
import accountmanager.gui.vanillaui.components.CompactTextInput;
import accountmanager.util.Config;
import accountmanager.util.MenuPrefs;
import accountmanager.util.WelcomeGate;
import accountmanager.util.macro.MacroExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiSetScreenMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private boolean acc$replacingScreen;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void acc$replaceScreen(Screen screen, CallbackInfo ci) {

        CompactTextInput.clearFocusedInput();
        if (!acc$replacingScreen) MacroExecutor.recordRecentGuiScreen(screen);
        if (acc$replacingScreen || screen == null) return;

        if (PackHideState.isActive() && screen instanceof PauseScreen pauseScreen && pauseScreen.showsPauseMenu()) {
            acc$setScreen(new PauseScreen());
            ci.cancel();
            return;
        }

        if (!(screen instanceof TitleScreen)) return;

        if (!PackHideState.isActive() && WelcomeGate.shouldShow(Config.getGlobal())) {
            WelcomeGate.markShown(Config.getGlobal());
            acc$setScreen(new WelcomeScreen());
            ci.cancel();
            return;
        }

        if (PackHideState.isActive()) {
            acc$setScreen(new PanicTitleScreen());
            ci.cancel();
            return;
        }

        if (!accountmanager.util.LiteVariant.enabled() && MenuPrefs.customMainMenuEnabled()) {
            acc$setScreen(new accountmanager.gui.screen.TitleScreen());
            ci.cancel();
        }
    }

    @Unique
    private void acc$setScreen(Screen screen) {
        acc$replacingScreen = true;
        try {
            minecraft.gui.setScreen(screen);
        } finally {
            acc$replacingScreen = false;
        }
    }
}
