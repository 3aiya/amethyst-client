package com.amethystclient.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * The only class the shared UI code draws through. Each Minecraft version has its own copy that
 * maps these calls onto that version's GUI API, so widgets, pages and HUD modules stay the same in
 * every version.
 */
public final class Draw {
	/** The UI font (Quicksand, see assets/amethystclient/font/ui.json). */
	private static final FontDescription UI_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("amethystclient", "ui"));
	/** The other fonts in assets/amethystclient/font, by name (e.g. "lexend_20"). */
	private static final Map<String, FontDescription> SHARP_FONTS = new HashMap<>();
	/** The textures drawn with {@link #image}, by path. */
	private static final Map<String, Identifier> IMAGES = new HashMap<>();
	/** The styled text of recent strings, by font, so the same text isn't rebuilt every frame. */
	private static final Map<FontDescription, Map<String, Component>> STYLED = new HashMap<>();

	public final GuiGraphicsExtractor graphics;
	private final Font font = Minecraft.getInstance().font;
	private float alpha = 1f;

	public Draw(GuiGraphicsExtractor graphics) {
		this.graphics = graphics;
	}

	public int width() {
		return graphics.guiWidth();
	}

	public int height() {
		return graphics.guiHeight();
	}

	/** Screen pixels per GUI pixel. Drawing inside {@code scale(1 / guiScale())} works in screen pixels. */
	public float guiScale() {
		return (float) Minecraft.getInstance().getWindow().getGuiScale();
	}

	// ---- shapes ----

	public void fill(int x1, int y1, int x2, int y2, int color) {
		color = apply(color);
		if (x2 > x1 && y2 > y1 && color >>> 24 != 0) {
			graphics.fill(x1, y1, x2, y2, color);
		}
	}

	/** A rect fading from {@code top} to {@code bottom}. */
	public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
		if (x2 > x1 && y2 > y1) {
			graphics.fillGradient(x1, y1, x2, y2, apply(top), apply(bottom));
		}
	}

	/**
	 * Draws the ({@code u}, {@code v}, {@code uw}×{@code vh}) part of {@code mask}, tinted with
	 * {@code color}, stretched over ({@code x}, {@code y}, {@code w}×{@code h}): one textured quad.
	 */
	public void mask(Mask mask, int x, int y, int w, int h, int u, int v, int uw, int vh, int color) {
		color = apply(color);
		if (w > 0 && h > 0 && color >>> 24 != 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture(mask), x, y, u, v, w, h, uw, vh, mask.width, mask.height, color);
		}
	}

	private static Identifier texture(Mask mask) {
		if (mask.texture instanceof Identifier id) {
			return id;
		}
		NativeImage image = new NativeImage(mask.width, mask.height, false);
		for (int y = 0; y < mask.height; y++) {
			for (int x = 0; x < mask.width; x++) {
				image.setPixel(x, y, mask.alphaAt(x, y) << 24 | 0xFFFFFF);
			}
		}
		Identifier id = Identifier.fromNamespaceAndPath("amethystclient", "mask/" + mask.key);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Amethyst " + mask.key, image));
		mask.texture = id;
		return id;
	}

	/**
	 * Draws the whole of {@code assets/amethystclient/textures/<path>.png} ({@code texW}×{@code texH}
	 * pixels) stretched over ({@code x}, {@code y}, {@code w}×{@code h}).
	 */
	public void image(String path, int x, int y, int w, int h, int texW, int texH) {
		int color = apply(0xFFFFFFFF);
		if (w > 0 && h > 0 && color >>> 24 != 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, image(path), x, y, 0, 0, w, h, texW, texH, texW, texH, color);
		}
	}

	private static Identifier image(String path) {
		return IMAGES.computeIfAbsent(path, p -> Identifier.fromNamespaceAndPath("amethystclient", "textures/" + p + ".png"));
	}

	// ---- text ----

	public void text(String text, int x, int y, int color) {
		color = apply(color);
		// Very low alphas are drawn fully opaque by the font renderer, so skip them.
		if (color >>> 24 >= 8) {
			graphics.text(font, styled(text), x, y, color, false);
		}
	}

	public int textWidth(String text) {
		return font.width(styled(text));
	}

	private static Component styled(String text) {
		return styled(text, UI_FONT);
	}

	private static Component styled(String text, FontDescription uiFont) {
		Map<String, Component> cache = STYLED.computeIfAbsent(uiFont, f -> new HashMap<>());
		if (cache.size() > 512) {
			cache.clear();
		}
		return cache.computeIfAbsent(text, t -> Component.literal(t).withStyle(style -> style.withFont(uiFont)));
	}

	/** Like {@link #text}, in another font from assets/amethystclient/font (e.g. "lexend_20"). */
	public void text(String text, int x, int y, int color, String fontName) {
		color = apply(color);
		if (color >>> 24 >= 8) {
			FontDescription uiFont = font(fontName);
			graphics.text(font, styled(text, uiFont), x, y, color, false);
		}
	}

	public int textWidth(String text, String fontName) {
		return font.width(styled(text, font(fontName)));
	}

	private static FontDescription font(String name) {
		return SHARP_FONTS.computeIfAbsent(name, n -> new FontDescription.Resource(Identifier.fromNamespaceAndPath("amethystclient", n)));
	}

	public int lineHeight() {
		return font.lineHeight;
	}

	// ---- transform, clipping, layering ----

	public void push() {
		graphics.pose().pushMatrix();
	}

	public void translate(float x, float y) {
		graphics.pose().translate(x, y);
	}

	public void scale(float s) {
		graphics.pose().scale(s, s);
	}

	public void pop() {
		graphics.pose().popMatrix();
	}

	public void clip(int x1, int y1, int x2, int y2) {
		graphics.enableScissor(x1, y1, x2, y2);
	}

	public void unclip() {
		graphics.disableScissor();
	}

	/** Starts a new layer: everything drawn after this is on top of everything before, text included. */
	public void layer() {
		graphics.nextStratum();
	}

	/** Multiplies the alpha of everything drawn until it's reset to 1. Used for fade-ins. */
	public void alpha(float value) {
		alpha = Math.clamp(value, 0f, 1f);
	}

	private int apply(int color) {
		if (alpha >= 1f) {
			return color;
		}
		return ((int) ((color >>> 24) * alpha) << 24) | (color & 0x00FFFFFF);
	}
}
