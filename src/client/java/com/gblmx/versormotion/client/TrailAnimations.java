package com.gblmx.versormotion.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gblmx.versormotion.VersorParticles;
import net.minecraft.client.Minecraft;

/**
 * Client clocks and baked particle data for server-produced effects.
 *
 * <p>ARC entries are swing trails: they drive the blade boxes and the body
 * twist. BURST entries are field vortices: particles only. Both replay the
 * exact sample list the server baked, one batch per tick.
 */
public final class TrailAnimations {
	public static final int ARC = 0;
	public static final int BURST = 1;

	private static final float TWIST = (float) Math.toRadians(55.0);

	private static final class Trail {
		final long startTick;
		final int duration;
		final float[] points;
		final int kind;
		int emitted;

		Trail(long startTick, int duration, float[] points, int kind) {
			this.startTick = startTick;
			this.duration = duration;
			this.points = points;
			this.kind = kind;
		}
	}

	private static final Map<Integer, Trail> ACTIVE = new HashMap<>();
	private static int nextBurstId = -1;

	private TrailAnimations() {}

	public static void startArc(int entityId, int duration, float[] points) {
		start(entityId, duration, points, ARC);
	}

	public static void startBurst(int duration, float[] points) {
		start(nextBurstId--, duration, points, BURST);
	}

	private static void start(int id, int duration, float[] points, int kind) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || points == null || points.length < 6 || points.length % 3 != 0) {
			return;
		}
		ACTIVE.put(id, new Trail(minecraft.level.getGameTime(),
				Math.max(1, duration), points, kind));
	}

	/** @return 0..1 while active, -1 when inactive */
	public static float progress(int id) {
		Trail trail = ACTIVE.get(id);
		if (trail == null) {
			return -1f;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			ACTIVE.remove(id);
			return -1f;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float t = (minecraft.level.getGameTime() + partial - trail.startTick) / trail.duration;
		if (t >= 1f) {
			ACTIVE.remove(id);
			return -1f;
		}
		return Math.max(0f, t);
	}

	/** Body yaw twist for swings, 0 for bursts and inactive ids. */
	public static float twist(int id) {
		Trail trail = ACTIVE.get(id);
		if (trail == null || trail.kind != ARC) {
			return 0f;
		}
		float t = progress(id);
		return t < 0f ? 0f : TWIST * (t * t * (3f - 2f * t) * 2f - 1f);
	}

	public static float[] points(int id) {
		Trail trail = ACTIVE.get(id);
		return trail == null ? null : trail.points;
	}

	/** Ids that need an arc mesh drawn. */
	public static int[] arcIds() {
		List<Integer> ids = new ArrayList<>();
		for (Map.Entry<Integer, Trail> entry : ACTIVE.entrySet()) {
			if (entry.getValue().kind == ARC) {
				ids.add(entry.getKey());
			}
		}
		return toArray(ids);
	}

	public static int[] activeIds() {
		return toArray(new ArrayList<>(ACTIVE.keySet()));
	}

	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null) {
			ACTIVE.clear();
			return;
		}
		for (int id : activeIds()) {
			Trail trail = ACTIVE.get(id);
			if (trail == null) {
				continue;
			}
			float t = progress(id);
			if (t < 0f) {
				continue;
			}
			int total = trail.points.length / 3;
			int reached = Math.min(total, (int) Math.ceil(t * total));
			boolean particles = VersorClientConfig.get().swingParticles;
			for (; trail.emitted < reached; trail.emitted++) {
				int i = trail.emitted * 3;
				if (particles) {
					minecraft.level.addParticle(VersorParticles.SWING_SPARK,
							trail.points[i], trail.points[i + 1], trail.points[i + 2],
							0.0, 0.0, 0.0);
				}
			}
		}
	}

	private static int[] toArray(List<Integer> ids) {
		int[] result = new int[ids.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = ids.get(i);
		}
		return result;
	}

	public static void clear() {
		ACTIVE.clear();
	}
}
