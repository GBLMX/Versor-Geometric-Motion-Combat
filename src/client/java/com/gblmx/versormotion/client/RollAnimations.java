package com.gblmx.versormotion.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;

/**
 * Client-side roll clocks. The server sends only (entity, duration); the
 * animation starts on receipt and is sampled with the render partial tick.
 */
public final class RollAnimations {
	private record Roll(long startTick, int duration) {}

	private static final Map<Integer, Roll> ACTIVE = new HashMap<>();

	private RollAnimations() {}

	public static void start(int entityId, int duration) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		ACTIVE.put(entityId, new Roll(minecraft.level.getGameTime(), Math.max(1, duration)));
	}

	/** @return 0..1 while rolling, -1 when inactive */
	public static float progress(int entityId) {
		Roll roll = ACTIVE.get(entityId);
		if (roll == null) {
			return -1f;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			ACTIVE.remove(entityId);
			return -1f;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float t = (minecraft.level.getGameTime() + partial - roll.startTick) / roll.duration;
		if (t >= 1f) {
			ACTIVE.remove(entityId);
			return -1f;
		}
		return Math.max(0f, t);
	}

	/** Smoothstep, or -1 when inactive. */
	public static float eased(int entityId) {
		float t = progress(entityId);
		return t < 0f ? -1f : t * t * (3f - 2f * t);
	}

	/** Limb tuck weight: zero at both ends, one at mid-roll. */
	public static float tuck(int entityId) {
		float t = progress(entityId);
		return t < 0f ? 0f : (float) Math.sin(Math.PI * t);
	}
}
