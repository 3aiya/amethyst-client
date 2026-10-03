package accountmanager.mixin;

import accountmanager.ducks.ExternalButtonScreen;
import accountmanager.gui.screen.AccountsScreen;
import accountmanager.gui.screen.JoinMacroScreen;
import accountmanager.gui.screen.MultiConsoleScreen;
import accountmanager.gui.screen.MultiDisclaimerScreen;
import accountmanager.gui.screen.MultiScreen;
import accountmanager.gui.screen.ProxiesScreen;
import accountmanager.gui.screen.VoiceChatPromptScreen;
import accountmanager.modules.ClientModule;
import accountmanager.modules.PackHideState;
import accountmanager.util.Config;
import accountmanager.util.SavedProxy;
import accountmanager.util.ProxyManager;
import accountmanager.util.JoinMacroController;
import accountmanager.util.MacroManager;
import accountmanager.util.multi.MultiManager;
import java.util.Locale;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JoinMultiplayerScreen.class, priority = 2000)
public abstract class MultiplayerScreenMixin extends Screen implements ExternalButtonScreen {
    @Unique private static final int BUTTON_HEIGHT = 20;
    @Unique private static final int BUTTON_WIDTH = 60;
    @Unique private static final int MACRO_BUTTON_WIDTH = 50;
    @Unique private static final int MULTI_BUTTON_WIDTH = 50;
    @Unique private static final int STACK_WIDTH = 104;
    @Unique private static final int MARGIN = 4;
    @Unique private static final int GAP = 3;
    @Unique private static final int EXTERNAL_NONE = 0;
    @Unique private static final int EXTERNAL_VIA_FABRIC_PLUS = 1;
    @Unique private static final int EXTERNAL_REPLAY_RECORD = 2;
    @Unique private static final int EXTERNAL_OPSEC = 3;

    @Unique private Button acc$accountsButton;
    @Unique private Button acc$joinMacroButton;
    @Unique private Button acc$multiButton;
    @Unique private Button acc$proxiesButton;
    @Unique private Button acc$spoofButton;
    @Unique private Button acc$packsButton;
    @Unique private Button acc$libraryButton;
    @Unique private Button acc$recordButton;
    @Unique private boolean acc$voicePromptChecked;
    @Unique private boolean acc$meteorUiConfigSuppressed;
    @Unique private int acc$topButtonsLeft = MARGIN;

    @Shadow protected ServerSelectionList serverSelectionList;

    protected MultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void acc$repositionElements(CallbackInfo ci) {
        acc$layoutButtons();
        acc$maybeShowVoiceChatPrompt();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void acc$refreshMultiButton(CallbackInfo ci) {
        if (acc$multiButton == null) return;
        String label = acc$multiLabel();
        if (!label.equals(acc$multiButton.getMessage().getString())) {
            acc$multiButton.setMessage(Component.literal(label));
        }
    }

    @Unique
    private static String acc$multiLabel() {
        MultiManager manager = MultiManager.get();
        return manager.isActive() ? "Multi " + manager.readyFraction() : "Multi";
    }

    @Unique
    private void acc$maybeShowVoiceChatPrompt() {
        if (acc$voicePromptChecked) return;
        acc$voicePromptChecked = true;
        if (PackHideState.isActive()) return;
        if (!FabricLoader.getInstance().isModLoaded("voicechat")) return;
        ClientModule module = ClientModule.get();
        if (module == null || !module.isSpoofClientVanilla()) return;
        Config config = Config.getGlobal();
        if (config == null || config.voiceChatModdedPromptShown) return;
        config.voiceChatModdedPromptShown = true;
        config.save();
        Screen parent = this;

        this.minecraft.execute(() -> {
            if (this.minecraft.gui.screen() == parent) {
                this.minecraft.gui.setScreen(new VoiceChatPromptScreen(parent));
            }
        });
    }

    @Unique
    private void acc$layoutButtons() {
        acc$suppressMeteorWidgets();
        AbstractWidget via = null;
        AbstractWidget opsec = null;
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof AbstractWidget widget) || acc$isOwned(widget)) continue;
            int kind = acc$externalKind(widget);
            if (kind == EXTERNAL_REPLAY_RECORD) {
                acc$setVisible(widget, false);
            } else if (kind == EXTERNAL_VIA_FABRIC_PLUS) {
                via = widget;
            } else if (kind == EXTERNAL_OPSEC) {
                opsec = widget;
            }
        }

        boolean hidden = PackHideState.isActive();
        if (hidden) {
            acc$setVisible(acc$accountsButton, false);
            acc$setVisible(acc$joinMacroButton, false);
            acc$setVisible(acc$multiButton, false);
            acc$setVisible(acc$proxiesButton, false);
            acc$setVisible(acc$spoofButton, false);
            acc$setVisible(acc$packsButton, false);
            acc$setVisible(acc$libraryButton, false);
            acc$setVisible(acc$recordButton, false);
            acc$setVisible(via, false);
            acc$setVisible(opsec, false);
            return;
        }

        acc$ensureOwnedButtons();
        int topRight = this.width - MARGIN;
        int topAvailable = Math.max(4, this.width - MARGIN * 2);

        boolean lite = accountmanager.util.LiteVariant.enabled();
        int topCell = lite ? Math.max(1, (topAvailable - GAP * 3) / 4)
            : Math.max(1, (topAvailable - GAP * 4) / 5);
        int accountsW = Math.min(BUTTON_WIDTH, topCell);
        int proxiesW = Math.min(BUTTON_WIDTH, topCell);
        int libraryW = Math.min(BUTTON_WIDTH, topCell);
        int macroW = Math.min(MACRO_BUTTON_WIDTH, topCell);
        int multiW = Math.min(MULTI_BUTTON_WIDTH, topCell);
        int cursor = topRight;
        cursor -= accountsW;
        acc$place(acc$accountsButton, cursor, MARGIN, accountsW, BUTTON_HEIGHT);
        cursor -= GAP + proxiesW;
        acc$place(acc$proxiesButton, cursor, MARGIN, proxiesW, BUTTON_HEIGHT);
        cursor -= GAP + libraryW;
        acc$libraryButton.setMessage(acc$fitLabel(libraryW, "Library"));
        acc$place(acc$libraryButton, cursor, MARGIN, libraryW, BUTTON_HEIGHT);
        cursor -= GAP + macroW;
        acc$place(acc$joinMacroButton, cursor, MARGIN, macroW, BUTTON_HEIGHT);
        if (!lite) {
            cursor -= GAP + multiW;
            acc$place(acc$multiButton, cursor, MARGIN, multiW, BUTTON_HEIGHT);
            String multiLabel = acc$multiLabel();
            if (!multiLabel.equals(acc$multiButton.getMessage().getString())) {
                acc$multiButton.setMessage(Component.literal(multiLabel));
            }
        }
        acc$topButtonsLeft = Math.max(MARGIN, cursor);

        int footerRight = (this.width / 2) - 154 - GAP;
        int footerWidth = Math.max(60, Math.min(STACK_WIDTH, footerRight - MARGIN));
        int footerX = Math.max(MARGIN, footerRight - footerWidth);
        int footerY = Math.max(MARGIN, this.height - (BUTTON_HEIGHT * 2 + GAP + 8));
        acc$spoofButton.setMessage(acc$spoofClientLabel(footerWidth));
        acc$packsButton.setMessage(acc$bypassPacksLabel(footerWidth));
        acc$place(acc$spoofButton, footerX, footerY, footerWidth, BUTTON_HEIGHT);
        acc$place(acc$packsButton, footerX, footerY + BUTTON_HEIGHT + GAP, footerWidth, BUTTON_HEIGHT);

        int rightX = Math.min(this.width - MARGIN - STACK_WIDTH, (this.width / 2) + 154 + GAP);
        int rightWidth = Math.max(60, Math.min(STACK_WIDTH, this.width - MARGIN - rightX));
        int count = (FabricLoader.getInstance().isModLoaded("replaymod") ? 1 : 0) + (via != null ? 1 : 0) + (opsec != null ? 1 : 0);
        int rightY = Math.max(MARGIN, this.height - 8 - Math.max(0, count * BUTTON_HEIGHT + Math.max(0, count - 1) * GAP));
        int slot = 0;

        if (FabricLoader.getInstance().isModLoaded("replaymod")) {
            acc$recordButton.setMessage(acc$replayServerLabel(rightWidth));
            acc$place(acc$recordButton, rightX, rightY + slot++ * (BUTTON_HEIGHT + GAP), rightWidth, BUTTON_HEIGHT);
        } else {
            acc$setVisible(acc$recordButton, false);
        }
        if (via != null) {
            acc$place(via, rightX, rightY + slot++ * (BUTTON_HEIGHT + GAP), rightWidth, BUTTON_HEIGHT);
        }
        if (opsec != null) {
            acc$place(opsec, rightX, rightY + slot * (BUTTON_HEIGHT + GAP), rightWidth, BUTTON_HEIGHT);
        }
    }

    @Unique
    private void acc$ensureOwnedButtons() {
        if (acc$accountsButton == null) {
            acc$accountsButton = this.addRenderableWidget(Button.builder(Component.literal("Accounts"),
                ignored -> this.minecraft.gui.setScreen(new accountmanager.gui.screen.AccountsScreen(this))).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (acc$joinMacroButton == null) {
            acc$joinMacroButton = this.addRenderableWidget(Button.builder(Component.literal("Macro"),
                ignored -> this.minecraft.gui.setScreen(new JoinMacroScreen(this))).bounds(0, 0, MACRO_BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (acc$multiButton == null && !accountmanager.util.LiteVariant.enabled()) {

            acc$multiButton = this.addRenderableWidget(Button.builder(Component.literal("Multi"),
                ignored -> acc$openMulti()).bounds(0, 0, MULTI_BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (acc$proxiesButton == null) {
            acc$proxiesButton = this.addRenderableWidget(Button.builder(Component.literal("Proxies"),
                ignored -> this.minecraft.gui.setScreen(new accountmanager.gui.screen.ProxiesScreen(this))).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (acc$spoofButton == null) {
            acc$spoofButton = this.addRenderableWidget(Button.builder(Component.literal("Client"),
                ignored -> {
                    ClientModule module = ClientModule.get();
                    if (module != null) module.setSpoofClientVanilla(!module.isSpoofClientVanilla());
                    acc$layoutButtons();
                }).bounds(0, 0, STACK_WIDTH, BUTTON_HEIGHT).build());
        }
        if (acc$packsButton == null) {
            acc$packsButton = this.addRenderableWidget(Button.builder(Component.literal("Packs"),
                ignored -> {
                    ClientModule module = ClientModule.get();
                    if (module != null) module.setBypassResourcePack(!module.isBypassResourcePack());
                    acc$layoutButtons();
                }).bounds(0, 0, STACK_WIDTH, BUTTON_HEIGHT).build());
        }
        if (acc$libraryButton == null) {
            acc$libraryButton = this.addRenderableWidget(Button.builder(Component.literal("Library"),
                ignored -> acc$openPluginLibrary()).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (acc$recordButton == null) {
            acc$recordButton = this.addRenderableWidget(Button.builder(Component.literal("Replay"),
                ignored -> {
                    acc$toggleReplayServerRecording();
                    acc$layoutButtons();
                }).bounds(0, 0, STACK_WIDTH, BUTTON_HEIGHT).build());
        }
    }

    @Unique
    private void acc$openPluginLibrary() {
        this.minecraft.gui.setScreen(new accountmanager.gui.screen.PluginLibraryScreen(this));
    }

    @Unique
    private void acc$openMulti() {

        if (accountmanager.util.LiteVariant.enabled()) return;
        net.minecraft.client.multiplayer.ServerData selected = acc$selectedServerData();
        String address = selected == null ? "" : selected.ip;
        Runnable proceed = () -> {
            if (MultiManager.get().isActive()) {
                this.minecraft.gui.setScreen(new MultiConsoleScreen(this));
            } else {
                MultiManager.get().rememberSelectedServer(selected);
                this.minecraft.gui.setScreen(new MultiScreen(this, address));
            }
        };
        MultiDisclaimerScreen.open(this.minecraft, this, proceed);
    }

    @Unique
    private boolean acc$isOwned(AbstractWidget widget) {
        return widget == acc$accountsButton || widget == acc$joinMacroButton || widget == acc$multiButton
            || widget == acc$proxiesButton || widget == acc$spoofButton
            || widget == acc$packsButton || widget == acc$libraryButton || widget == acc$recordButton;
    }

    @Unique
    private net.minecraft.client.multiplayer.ServerData acc$selectedServerData() {
        if (serverSelectionList == null) return null;
        ServerSelectionList.Entry selected = serverSelectionList.getSelected();
        if (selected instanceof ServerSelectionList.OnlineServerEntry online) {
            return online.getServerData();
        }
        return null;
    }

    @Unique
    private static void acc$place(AbstractWidget widget, int x, int y, int width, int height) {
        if (widget == null) return;
        widget.setX(Math.max(MARGIN, x));
        widget.setY(Math.max(MARGIN, y));
        widget.setSize(Math.max(1, width), Math.max(1, height));
        acc$setVisible(widget, true);
    }

    @Unique
    private static void acc$setVisible(AbstractWidget widget, boolean visible) {
        if (widget == null) return;
        widget.visible = visible;
        widget.active = visible;
    }

    @Unique
    private Component acc$spoofClientLabel(int width) {
        ClientModule module = ClientModule.get();
        boolean enabled = module != null && module.isSpoofClientVanilla();
        return acc$fitLabel(width, enabled ? "Client: Vanilla" : "Client: Modded", enabled ? "Vanilla" : "Modded", "Client");
    }

    @Unique
    private Component acc$bypassPacksLabel(int width) {
        ClientModule module = ClientModule.get();
        boolean enabled = module != null && module.isBypassResourcePack();
        return acc$fitLabel(width, enabled ? "Packs: Bypass" : "Packs: Normal", enabled ? "Bypass" : "Normal", "Packs");
    }

    @Unique
    private Component acc$replayServerLabel(int width) {
        boolean enabled = acc$getReplayBoolean("RECORD_SERVER", true);
        return acc$fitLabel(width, enabled ? "Replay: On" : "Replay: Off", enabled ? "Rec: On" : "Rec: Off", "Replay");
    }

    @Unique
    private Component acc$fitLabel(int width, String... candidates) {
        int available = Math.max(1, width - 8);
        for (String candidate : candidates) {
            if (this.font.width(candidate) <= available) return Component.literal(candidate);
        }
        return Component.literal(candidates[candidates.length - 1]);
    }

    @Unique
    private int acc$externalKind(AbstractWidget widget) {
        String label = widget.getMessage().getString();
        String normalized = label.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace(".", "");
        String className = widget.getClass().getName().toLowerCase(Locale.ROOT);
        if (FabricLoader.getInstance().isModLoaded("viafabricplus") && "ViaFabricPlus".equals(label)) return EXTERNAL_VIA_FABRIC_PLUS;
        if (FabricLoader.getInstance().isModLoaded("replaymod")
            && (className.contains("replaymod") || normalized.contains("recordserver") || normalized.contains("replaymodguisettingsrecordserver"))) {
            return EXTERNAL_REPLAY_RECORD;
        }
        if (className.contains("opsec") || normalized.contains("opsec")) return EXTERNAL_OPSEC;
        return EXTERNAL_NONE;
    }

    @Unique
    private void acc$suppressMeteorWidgets() {
        if (!FabricLoader.getInstance().isModLoaded("meteor-client")) return;
        if (!acc$meteorUiConfigSuppressed) {
            acc$meteorUiConfigSuppressed = true;
            acc$disableMeteorMultiplayerUiConfig();
        }
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof Button button) || acc$isOwned(button)) continue;
            String label = button.getMessage().getString();
            if ("Accounts".equals(label) || "Proxies".equals(label)) acc$setVisible(button, false);
        }
    }

    @Unique
    private void acc$toggleReplayServerRecording() {
        boolean enabled = acc$getReplayBoolean("RECORD_SERVER", true);
        acc$setReplayBoolean("RECORD_SERVER", !enabled);
    }

    @Unique
    private static boolean acc$getReplayBoolean(String settingField, boolean fallback) {
        try {
            Object settings = acc$replaySettingsRegistry();
            Object key = Class.forName("com.replaymod.recording.Setting").getField(settingField).get(null);
            Object value = settings.getClass().getMethod("get", Class.forName("com.replaymod.core.SettingsRegistry$SettingKey")).invoke(settings, key);
            return value instanceof Boolean bool ? bool : fallback;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return fallback;
        }
    }

    @Unique
    private static void acc$setReplayBoolean(String settingField, boolean value) {
        try {
            Object settings = acc$replaySettingsRegistry();
            Object key = Class.forName("com.replaymod.recording.Setting").getField(settingField).get(null);
            Class<?> settingKeyClass = Class.forName("com.replaymod.core.SettingsRegistry$SettingKey");
            settings.getClass().getMethod("set", settingKeyClass, Object.class).invoke(settings, key, value);
            settings.getClass().getMethod("save").invoke(settings);
        } catch (ReflectiveOperationException | LinkageError ignored) {  }
    }

    @Unique
    private static Object acc$replaySettingsRegistry() throws ReflectiveOperationException {
        Object replayMod = Class.forName("com.replaymod.core.ReplayMod").getField("instance").get(null);
        return replayMod.getClass().getMethod("getSettingsRegistry").invoke(replayMod);
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void acc$disableMeteorMultiplayerUiConfig() {
        try {
            Class<?> configClass = Class.forName("meteordevelopment.meteorclient.systems.config.Config");
            Object config = configClass.getMethod("get").invoke(null);
            Class<?> buttonPositionClass = Class.forName("meteordevelopment.meteorclient.systems.config.Config$ButtonPosition");
            Object hidden = Enum.valueOf((Class<? extends Enum>) buttonPositionClass.asSubclass(Enum.class), "Hidden");
            acc$setMeteorSetting(configClass.getField("accountButtonAnchor").get(config), hidden);
            acc$setMeteorSetting(configClass.getField("proxiesButtonAnchor").get(config), hidden);
            acc$setMeteorSetting(configClass.getField("showAccountStatus").get(config), false);
            acc$setMeteorSetting(configClass.getField("showProxiesStatus").get(config), false);
        } catch (ReflectiveOperationException ignored) {  }
    }

    @Unique
    private static void acc$setMeteorSetting(Object setting, Object value) throws ReflectiveOperationException {
        setting.getClass().getSuperclass().getMethod("set", Object.class).invoke(setting, value);
    }

    @Override
    public void acc$renderExternalButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        acc$layoutButtons();
        if (PackHideState.isActive()) return;
        int leftTextWidth = Math.max(0, acc$topButtonsLeft - MARGIN - GAP);
        String username = this.minecraft.getUser().getName();
        if (leftTextWidth > 8) {
            graphics.text(this.font, acc$fitPlain("Logged in as " + username, leftTextWidth), MARGIN, MARGIN, 0xFFFFFFFF, false);
        }
        SavedProxy proxy = ProxyManager.get().getEnabled();
        int statusY = MARGIN + 12;
        if (proxy != null) {
            String proxyLabel = "Using proxy " + proxy.address + ":" + proxy.port;
            if (leftTextWidth > 8) graphics.text(this.font, acc$fitPlain(proxyLabel, leftTextWidth), MARGIN, statusY, 0xFFAFAFAF, false);
            statusY += 12;
        }
        String macroName = JoinMacroController.selectedMacroName();
        String macroLabel;
        int macroColor;
        if (macroName.isBlank()) {
            macroLabel = "Join Macro: none";
            macroColor = 0xFF8F8A8A;
        } else if (MacroManager.get().get(macroName) == null) {
            macroLabel = "Join Macro missing: " + macroName;
            macroColor = 0xFFFF6B6B;
        } else {
            macroLabel = "Join Macro: " + macroName + " - " + JoinMacroController.modeSummary();
            macroColor = 0xFF66E08A;
        }
        if (leftTextWidth > 8) graphics.text(this.font, acc$fitPlain(macroLabel, leftTextWidth), MARGIN, statusY, macroColor, false);
    }

    @Unique
    private String acc$fitPlain(String label, int maxWidth) {
        if (label == null) return "";
        if (this.font.width(label) <= maxWidth) return label;
        return this.font.plainSubstrByWidth(label, Math.max(1, maxWidth - 4));
    }

}
