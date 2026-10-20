package com.gblmx.versormotion.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Client-side tornado replay: the server ships one baked funnel, the client
 * spins it around its axis, fades it in and out, and emits mist and debris
 * particles from the same point cloud.
 *
 * <p>Simulation here is presentation only: rotation speed, particle jitter and
 * fade are local, while the funnel geometry stays exactly what the server
 * baked.
 */
public final class TornadoAnimations {
	/** Angular speed of the funnel, in radians per second. */
	public static final double SPIN = 2.4;
	/** Ticks to fade in and fade out. */
	private static final float FADE_IN = 16f;
	private static final float FADE_OUT = 40f;
	private static final int MIST_PER_TICK = 8;
	private static final int DEBRIS_PER_TICK = 3;

	/** Immutable per-frame view of an active funnel. */
	public record Snapshot(double x, double y, double z, float[] points,
			double angle, float alpha) {}

	private static final class Funnel {
		final double x;
		final double y;
		final double z;
		final long startTick;
		final int duration;
		final float[] points;

		Funnel(double x, double y, double z, long startTick, int duration, float[] points) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.startTick = startTick;
			this.duration = duration;
			this.points = points;
		}
	}

	private static final List<Funnel> ACTIVE = new ArrayList<>();
	private static final Random RANDOM = new Random();

	private TornadoAnimations() {}

	public static void start(double x, double y, double z, int duration, float[] points) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || points == null || points.length < 6 || points.length % 3 != 0) {
			return;
		}
		ACTIVE.add(new Funnel(x, y, z, minecraft.level.getGameTime(),
				Math.max(1, duration), points));
	}

	/**
	 * Prunes finished funnels and returns the active ones rotated to now.
	 *
	 * @param partial partial tick for frame-rate-independent spinning
	 */
	public static List<Snapshot> active(float partial) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			ACTIVE.clear();
			return List.of();
		}
		long gameTime = minecraft.level.getGameTime();
		List<Snapshot> snapshots = new ArrayList<>(ACTIVE.size());
		for (int i = ACTIVE.size() - 1; i >= 0; i--) {
			Funnel funnel = ACTIVE.get(i);
			double elapsedTicks = gameTime + partial - funnel.startTick;
			float t = (float) (elapsedTicks / funnel.duration);
			if (t >= 1f) {
				ACTIVE.remove(i);
				continue;
			}
			float fadeIn = (float) Math.min(1.0, elapsedTicks / FADE_IN);
			float fadeOut = (float) Math.min(1.0, (funnel.duration - elapsedTicks) / FADE_OUT);
			float alpha = Math.min(fadeIn, fadeOut) * 0.6f;
			double angle = elapsedTicks / 20.0 * SPIN;
			snapshots.add(new Snapshot(funnel.x, funnel.y, funnel.z, funnel.points, angle, alpha));
		}
		return snapshots;
	}

	/** Emits mist and ground debris around every active funnel. */
	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null) {
			ACTIVE.clear();
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		List<Snapshot> funnels = active(partial);
		for (Snapshot funnel : funnels) {
			double cos = Math.cos(funnel.angle());
			double sin = Math.sin(funnel.angle());
			for (int i = 0; i < MIST_PER_TICK; i++) {
				double[] p = sample(funnel, cos, sin, RANDOM.nextDouble());
				minecraft.level.addParticle(ParticleTypes.CLOUD,
						p[0], p[1], p[2], 0.0, 0.02, 0.0);
			}
			for (int i = 0; i < DEBRIS_PER_TICK; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2.0;
				double radius = 1.5 + RANDOM.nextDouble() * 2.5;
				double x = funnel.x() + Math.cos(angle) * radius;
				double z = funnel.z() + Math.sin(angle) * radius;
				minecraft.level.addParticle(ParticleTypes.ASH,
						x, funnel.y() + 0.2, z,
						-Math.sin(angle) * 0.15, 0.08 + RANDOM.nextDouble() * 0.1,
						Math.cos(angle) * 0.15);
			}
		}
	}

	/** Picks one funnel point by index and rotates it around the axis. */
	private static double[] sample(Snapshot funnel, double cos, double sin, double pick) {
		float[] points = funnel.points();
		int count = points.length / 3;
		int index = Math.min(count - 1, (int) (pick * count)) * 3;
		double dx = points[index] - funnel.x();
		double dz = points[index + 2] - funnel.z();
		return new double[]{
				funnel.x() + dx * cos - dz * sin,
				points[index + 1] + RANDOM.nextGaussian() * 0.06,
				funnel.z() + dx * sin + dz * cos};
	}

	public static void clear() {
		ACTIVE.clear();
	}
}
