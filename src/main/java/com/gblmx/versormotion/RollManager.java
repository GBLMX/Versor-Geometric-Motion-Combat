package com.gblmx.versormotion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.gblmx.versormotion.net.RollPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative dodge roll: a short look-directed impulse, a burst of
 * invulnerability, and a cooldown. The client only receives the state change.
 */
public final class RollManager {
	public static final int DURATION_TICKS = 10;
	public static final int COOLDOWN_TICKS = 20;
	private static final double IMPULSE = 0.6;

	private static final Map<UUID, Long> LAST_ROLL = new HashMap<>();

	private RollManager() {}

	public static boolean start(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST_ROLL.get(player.getUUID());
		if (last != null && now - last < COOLDOWN_TICKS) {
			return false;
		}
		LAST_ROLL.put(player.getUUID(), now);

		Vec3 look = player.getLookAngle();
		double dx = look.x;
		double dz = look.z;
		double length = Math.hypot(dx, dz);
		if (length < 1e-4) {
			dx = 0.0;
			dz = 1.0;
		} else {
			dx /= length;
			dz /= length;
		}
		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(dx * IMPULSE, Math.max(velocity.y, 0.0), dz * IMPULSE);
		player.syncVelocity = true;
		player.setInvulnerableTime(DURATION_TICKS);

		RollPayload payload = new RollPayload(player.getId(), DURATION_TICKS);
		ServerPlayNetworking.send(player, payload);
		for (ServerPlayer tracker : PlayerLookup.tracking(player)) {
			if (tracker != player) {
				ServerPlayNetworking.send(tracker, payload);
			}
		}
		return true;
	}
}
