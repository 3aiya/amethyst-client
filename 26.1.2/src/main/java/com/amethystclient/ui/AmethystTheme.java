package com.amethystclient.ui;

import com.amethystclient.hud.HudConfig;
import java.util.List;

/**
 * The launcher's colour tokens (see AMETHYST_CLIENT_UI_GUIDE.md §2). Every widget reads its colours
 * from here; never hard-code a hex in a widget.
 *
 * <p>Only the dark-family themes are offered: a light theme over the world render is hard to read.
 */
public record AmethystTheme(
		String name,
		int accent, int accentHover, int accentPressed, int accentFg,
		int bg, int bgSidebar, int bgElevated, int bgCard, int bgCardHover,
		int border, int borderLight) {

	public static final AmethystTheme DARK = new AmethystTheme("Dark",
			0xFFFF00FF, 0xFFE000E0, 0xFFC400C4, 0xFF000000,
			0xFF0D0D10, 0xFF09090B, 0xFF17171B, 0xFF1B1B20, 0xFF232329,
			0xFF2A2A30, 0xFF34343C);

	public static final AmethystTheme BLUE = new AmethystTheme("Blue",
			0xFF4C9EFF, 0xFF3B8CE8, 0xFF2A76C8, 0xFF04101F,
			0xFF0B111D, 0xFF070C15, 0xFF131C2C, 0xFF172134, 0xFF1E2A42,
			0xFF233149, 0xFF2F4060);

	public static final AmethystTheme GREEN = new AmethystTheme("Green",
			0xFF3AE07A, 0xFF2BC566, 0xFF1FA553, 0xFF04140A,
			0xFF0A1210, 0xFF060D0B, 0xFF111E18, 0xFF15241C, 0xFF1C3126,
			0xFF21362A, 0xFF2C4A38);

	public static final List<AmethystTheme> ALL = List.of(DARK, BLUE, GREEN);

	// Text ramp (Tailwind zinc), shared by the dark-family themes.
	public static final int TEXT_HEADLINE = 0xFFFAFAFA;
	public static final int TEXT_PRIMARY = 0xFFF4F4F5;
	public static final int TEXT_SECONDARY = 0xFFD4D4D8;
	public static final int TEXT_HOVER = 0xFFE4E4E7;
	public static final int TEXT_LABEL = 0xFFA1A1AA;
	public static final int TEXT_MUTED = 0xFF71717A;
	public static final int TEXT_FAINT = 0xFF52525B;
	public static final int TEXT_SEPARATOR = 0xFF3F3F46;

	// Status colours.
	public static final int DANGER = 0xFFEF4444;
	public static final int DANGER_HOVER = 0xFFDC2626;
	public static final int SUCCESS = 0xFF10B981;
	public static final int SUCCESS_TEXT = 0xFF34D399;
	public static final int WARNING = 0xFFFBBF24;

	public static final int WHITE = 0xFFFFFFFF;
	public static final int BACKDROP = 0x99000000;

	/** The theme picked in the HUD settings, Dark when the saved name is unknown. */
	public static AmethystTheme current() {
		return byName(HudConfig.get().theme);
	}

	public static AmethystTheme byName(String name) {
		for (AmethystTheme theme : ALL) {
			if (theme.name.equalsIgnoreCase(name)) {
				return theme;
			}
		}
		return DARK;
	}

	/** {@code argb} with a new alpha, e.g. {@code withAlpha(accent, 0.15f)}. */
	public static int withAlpha(int argb, float a) {
		return ((int) (Math.clamp(a, 0f, 1f) * 255) << 24) | (argb & 0x00FFFFFF);
	}

	/** Linear blend from {@code a} to {@code b}, alpha included. */
	public static int lerp(int a, int b, float t) {
		t = Math.clamp(t, 0f, 1f);
		int ca = (int) ((a >>> 24) + ((b >>> 24) - (a >>> 24)) * t);
		int cr = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int cg = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int cb = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return ca << 24 | cr << 16 | cg << 8 | cb;
	}

	/** The disabled look: 60% greyscale, 70% brightness. */
	public static int disabled(int argb) {
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		int grey = (int) (r * 0.299 + g * 0.587 + b * 0.114);
		r = (int) ((r + (grey - r) * 0.6f) * 0.7f);
		g = (int) ((g + (grey - g) * 0.6f) * 0.7f);
		b = (int) ((b + (grey - b) * 0.6f) * 0.7f);
		return (argb & 0xFF000000) | r << 16 | g << 8 | b;
	}
}
