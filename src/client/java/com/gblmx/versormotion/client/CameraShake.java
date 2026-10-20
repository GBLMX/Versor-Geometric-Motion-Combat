package com.gblmx.versormotion.client;

import net.minecraft.client.Minecraft;

/** Decaying camera impulse; the latest hit replaces the previous one. */
public final class CameraShake {
	private static long startTick;
	private static int duration;
	private static float amplitude;

	private CameraShake() {}

	public static void start(float strength, int ticks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		startTick = minecraft.level.getGameTime();
		duration = Math.max(1, ticks);
		amplitude = Math.max(0.0f, Math.min(1.0f, strength));
	}

	/** 1 at impact, decaying to 0; consumes the shake when it finishes. */
	public static float envelope(Minecraft minecraft, float partial) {
		if (duration <= 0 || minecraft.level == null) {
			return 0.0f;
		}
		float t = (minecraft.level.getGameTime() + partial - startTick) / duration;
		if (t >= 1.0f) {
			duration = 0;
			return 0.0f;
		}
		float remain = 1.0f - Math.max(0.0f, t);
		return amplitude * remain * remain;
	}

	public static void clear() {
		duration = 0;
	}
}
