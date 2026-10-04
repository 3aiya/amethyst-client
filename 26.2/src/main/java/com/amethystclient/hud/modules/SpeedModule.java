package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.Game;
import com.amethystclient.hud.setting.ModeSetting;
import java.util.Arrays;
import java.util.Locale;

/** How fast you're moving, averaged over the last half second. */
public class SpeedModule extends SimpleModule {
	private static final int TICKS = 10;

	private final ModeSetting axes;
	private final ModeSetting unit;
	/** Distance moved in each of the last {@link #TICKS} ticks. */
	private final double[] distances = new double[TICKS];
	private int index;
	private double lastX;
	private double lastY;
	private double lastZ;
	private boolean hasLast;

	public SpeedModule() {
		super("speed", "Speed", "How fast you're moving", Category.WORLD, false, Position.stacked(8));
		axes = mode("axes", "Measure", "Horizontal", "Horizontal", "3D");
		unit = mode("unit", "Unit", "b/s", "b/s", "km/h");
	}

	@Override
	public void onJoinWorld() {
		hasLast = false;
		Arrays.fill(distances, 0);
	}

	@Override
	public void tick() {
		double x = Game.x();
		double y = Game.y();
		double z = Game.z();
		if (hasLast) {
			double dx = x - lastX;
			double dy = axes.get().equals("3D") ? y - lastY : 0;
			double dz = z - lastZ;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			// A teleport isn't movement.
			distances[index] = distance > 10 ? 0 : distance;
			index = (index + 1) % TICKS;
		}
		lastX = x;
		lastY = y;
		lastZ = z;
		hasLast = true;
	}

	/** Blocks per second. */
	private double speed() {
		double sum = 0;
		for (double distance : distances) {
			sum += distance;
		}
		return sum / TICKS * 20;
	}

	@Override
	protected String label() {
		return "SPEED";
	}

	@Override
	protected String value() {
		double speed = unit.get().equals("km/h") ? speed() * 3.6 : speed();
		return String.format(Locale.ROOT, "%.1f %s", speed, unit.get());
	}

	@Override
	protected String widthSample() {
		return "00.0 " + unit.get();
	}
}
