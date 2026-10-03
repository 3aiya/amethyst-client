package com.amethystclient.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/** Hosts a shared {@link Page} as a Minecraft screen in this version. */
public final class PageScreen extends Screen {
	private final Page page;
	private final Screen parent;

	private PageScreen(Page page, Screen parent) {
		super(Text.literal("Amethyst Client"));
		this.page = page;
		this.parent = parent;
		page.attach(this::close);
	}

	public static void open(Page page) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.setScreen(new PageScreen(page, client.currentScreen));
	}

	public static boolean isOpen(Class<? extends Page> type) {
		return MinecraftClient.getInstance().currentScreen instanceof PageScreen screen && type.isInstance(screen.page);
	}

	@Override
	protected void init() {
		page.resize(width, height);
	}

	@Override
	public void renderBackground(DrawContext graphics, int mouseX, int mouseY, float delta) {
		// The page draws its own backdrop, without the vanilla blur.
	}

	@Override
	public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
		page.render(new Draw(graphics), mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(Click event, boolean doubleClick) {
		return page.mouseClicked(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseDragged(Click event, double dx, double dy) {
		page.mouseDragged(event.x(), event.y(), event.button());
		return true;
	}

	@Override
	public boolean mouseReleased(Click event) {
		page.mouseReleased(event.x(), event.y(), event.button());
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return page.mouseScrolled(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyInput event) {
		return page.keyPressed(event.key()) || super.keyPressed(event);
	}

	@Override
	public boolean shouldPause() {
		return page.pausesGame();
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	@Override
	public void removed() {
		page.removed();
	}
}
