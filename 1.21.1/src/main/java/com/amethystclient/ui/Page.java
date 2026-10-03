package com.amethystclient.ui;

/**
 * A full screen built from the Amethyst widgets. It holds all the screen's logic and is shared by
 * every Minecraft version; {@link PageScreen} is the thin per-version wrapper that feeds it input.
 */
public abstract class Page {
	protected int width;
	protected int height;
	private Runnable closer = () -> {
	};

	void attach(Runnable closer) {
		this.closer = closer;
	}

	public void resize(int width, int height) {
		this.width = width;
		this.height = height;
	}

	public abstract void render(Draw d, int mx, int my);

	public boolean mouseClicked(double mx, double my, int button) {
		return false;
	}

	public void mouseDragged(double mx, double my, int button) {
	}

	public void mouseReleased(double mx, double my, int button) {
	}

	public boolean mouseScrolled(double mx, double my, double amount) {
		return false;
	}

	/** A GLFW key code. Return true to stop the default handling (Esc closes the page). */
	public boolean keyPressed(int key) {
		return false;
	}

	/** Called when the page goes away, however it was closed. */
	public void removed() {
	}

	public boolean pausesGame() {
		return false;
	}

	/** Closes the page and goes back to the previous screen. */
	protected final void close() {
		closer.run();
	}
}
