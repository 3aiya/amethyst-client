package accountmanager.util;

import accountmanager.modules.PackHideState;

public final class MenuPrefs {
    private MenuPrefs() {
    }

    public static boolean customMainMenuEnabled() {

        if (LiteVariant.enabled()) return false;
        Config config = Config.getGlobal();
        return config == null || config.customMainMenu;
    }

    public static boolean vanillaMenuVisuals() {
        return PackHideState.isActive() || !customMainMenuEnabled();
    }
}
