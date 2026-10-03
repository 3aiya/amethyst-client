package com.amethystclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Hosts a shared {@link Page} as a Minecraft screen in this version. */
public final class PageScreen extends Screen {
	private final Page page;
	private final Screen parent;

	private PageScreen(Page page, Screen parent) {
		super(Component.literal("Amethyst Client"));
		this.page = page;
		this.parent = parent;
		page.attach(this::onClose);
	}

	public static void open(Page page) {
		Minecraft client = Minecraft.getInstance();
		client.setScreen(new PageScreen(page, client.screen));
	}

	public static boolean isOpen(Class<? extends Page> type) {
		return Minecraft.getInstance().screen instanceof PageScreen screen && type.isInstance(screen.page);
	}

	@Override
	protected void init() {
		page.resize(width, height);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// The page draws its own backdrop, without the vanilla blur.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		page.render(new Draw(graphics), mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return page.mouseClicked(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		page.mouseDragged(event.x(), event.y(), event.button());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		page.mouseReleased(event.x(), event.y(), event.button());
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return page.mouseScrolled(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return page.keyPressed(event.key()) || super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return page.pausesGame();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		page.removed();
	}
}
