package accountmanager.util;

import accountmanager.AccountsMod;
import net.minecraft.util.Util;

import java.util.Locale;

public final class Links {
    public static final String DISCORD = "https://example.com/discord";
    public static final String COMMUNITY_DISCORD = "https://example.com/community";
    public static final String KOFI = "https://example.com/donate";
    public static final String CRYPTO_DONATE = "https://example.com/crypto";
    public static final String WEBSITE = "https://example.com";

    private Links() {
    }

    public static void open(String url) {
        if (!isOpenableUrl(url)) {
            AccountsMod.LOG.warn("[Client] Refused to open non-http(s) URL: {}", url);
            return;
        }
        try {
            Util.getPlatform().openUri(url);
        } catch (Throwable ignored) {  }
    }

    public static boolean isOpenableUrl(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:");
    }
}
