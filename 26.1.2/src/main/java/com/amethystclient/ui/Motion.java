package com.amethystclient.ui;

import com.amethystclient.hud.HudConfig;

/**
 * A value that eases (ease-out cubic) to its target over a fixed duration. With "Reduce motion" on
 * it jumps straight to the target.
 */
public final class Motion {
	private final int durationMs;
	private float from;
	private float to;
	private long startNanos;

	public Motion(float initial, int durationMs) {
		this.durationMs = durationMs;
		this.from = initial;
		this.to = initial;
	}

	public void set(float target) {
		if (target != to) {
			from = get();
			to = target;
			startNanos = System.nanoTime();
		}
	}

	/** Jumps to {@code value} without animating. */
	public void snap(float value) {
		from = value;
		to = value;
	}

	public float target() {
		return to;
	}

	public float get() {
		if (from == to || reduced()) {
			return to;
		}
		float t = (System.nanoTime() - startNanos) / 1_000_000f / durationMs;
		if (t >= 1f) {
			from = to;
			return to;
		}
		float eased = 1f - (1f - t) * (1f - t) * (1f - t);
		return from + (to - from) * eased;
	}

	public static boolean reduced() {
		return HudConfig.get().reduceMotion;
	}
}
