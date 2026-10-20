package com.gblmx.versormotion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gblmx.versormotion.combat.ArcSweep;
import com.gblmx.versormotion.combat.GuardPlane;
import com.gblmx.versormotion.net.CameraShakePayload;
import com.gblmx.versormotion.net.Payloads;
import com.gblmx.versormotion.net.SwingPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative geometric combat.
 *
 * <p>Every attack is adjudicated from the server's copy of the attacker: the
 * blade arc is baked once from the eye position and yaw, then each nearby
 * living entity whose box intersects the swept arc takes damage. The vanilla
 * target is excluded because vanilla already applies its own hit; the arc is
 * the follow-through that catches the rest of the swing. Clients receive the
 * baked arc and only replay it.
 */
public final class CombatManager {
	/** Swing presentation length in ticks. */
	public static final int SWING_TICKS = 6;
	/** Minimum spacing between adjudicated swings, in ticks. */
	public static final int SWING_COOLDOWN_TICKS = 4;
	/** Base arc damage before the vanilla charge scale. */
	public static final float ARC_DAMAGE = 2.0f;
	public static final double KNOCKBACK = 0.3;

	private static final Map<UUID, Long> LAST_SWING = new HashMap<>();

	private CombatManager() {}

	public static void init() {
		AttackEntityCallback.EVENT.register(CombatManager::onAttack);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				LAST_SWING.remove(handler.getPlayer().getUUID()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (blocked || taken <= 0f || !(entity instanceof ServerPlayer player)) {
				return;
			}
			ServerPlayNetworking.send(player,
					new CameraShakePayload(Math.min(1.0f, taken / 8.0f), 8));
		});
	}

	private static InteractionResult onAttack(Player player, Level level, InteractionHand hand,
			Entity target, EntityHitResult hitResult) {
		if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer server
				&& target instanceof LivingEntity living) {
			if (living instanceof ServerPlayer defender && parried(server, defender)) {
				return InteractionResult.FAIL;
			}
			swing(server, target);
		}
		return InteractionResult.PASS;
	}

	/**
	 * Shield parry: the defender must be blocking with a shield and facing the
	 * attacker, and the baked blade arc must cross the guard plane inside
	 * shield reach. The hit cancels the whole attack (no arc damage) and pushes
	 * the attacker back.
	 */
	private static boolean parried(ServerPlayer attacker, ServerPlayer defender) {
		if (!defender.isBlocking() || !defender.getUseItem().is(Items.SHIELD)) {
			return false;
		}
		Vec3 look = defender.getLookAngle();
		Vec3 toAttacker = attacker.position().subtract(defender.position());
		double distance = toAttacker.length();
		if (distance < 1e-6 || look.dot(toAttacker.scale(1.0 / distance)) < 0.45) {
			return false;
		}
		Vec3 eye = defender.getEyePosition();
		double d = -(look.x * eye.x + look.y * eye.y + look.z * eye.z);
		GuardPlane.Hit hit = GuardPlane.intersect(bakeArc(attacker),
				eye.x, eye.y, eye.z, look.x, look.y, look.z, d, GuardPlane.REACH);
		if (hit == null) {
			return false;
		}

		ServerLevel level = defender.level();
		level.playSound(null, hit.x(), hit.y(), hit.z(),
				SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.0f);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT,
				hit.x(), hit.y(), hit.z(), 10, 0.2, 0.2, 0.2, 0.05);
		attacker.push(toAttacker.x / distance * 0.4, 0.2, toAttacker.z / distance * 0.4);
		ServerPlayNetworking.send(attacker, new CameraShakePayload(0.6f, 6));
		return true;
	}

	private static double reach(ServerPlayer player) {
		return Math.clamp(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE),
				ArcSweep.MIN_RADIUS, ArcSweep.MAX_RADIUS);
	}

	private static double[] bakeArc(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		return ArcSweep.bake(eye.x, eye.y, eye.z,
				Math.toRadians(player.getYRot()), reach(player), ArcSweep.SAMPLES);
	}

	/**
	 * Adjudicates one swing and broadcasts its baked arc.
	 *
	 * @param directTarget entity vanilla is about to damage, excluded from the
	 *                     arc to avoid applying two hits to one target
	 * @return number of extra entities the arc damaged
	 */
	public static int swing(ServerPlayer player, Entity directTarget) {
		ServerLevel level = player.level();
		if (player.isSpectator()) {
			return 0;
		}
		long now = level.getGameTime();
		Long last = LAST_SWING.get(player.getUUID());
		if (last != null && now - last < SWING_COOLDOWN_TICKS) {
			return 0;
		}
		LAST_SWING.put(player.getUUID(), now);

		Vec3 eye = player.getEyePosition();
		double radius = reach(player);
		double[] arc = bakeArc(player);

		AABB search = new AABB(
				eye.x - radius - 1.0, eye.y - 2.5, eye.z - radius - 1.0,
				eye.x + radius + 1.0, eye.y + 2.5, eye.z + radius + 1.0);
		List<Entity> candidates = level.getEntities(player, search,
				entity -> entity instanceof LivingEntity && entity != directTarget
						&& entity.isAlive() && !entity.isSpectator());

		float scale = player.getAttackStrengthScale(0.5f);
		float damage = ARC_DAMAGE * (0.2f + 0.8f * scale * scale);
		DamageSource source = level.damageSources().playerAttack(player);
		int hits = 0;
		for (Entity candidate : candidates) {
			LivingEntity living = (LivingEntity) candidate;
			AABB box = living.getBoundingBox();
			if (!ArcSweep.hits(arc, ArcSweep.BLADE_RADIUS,
					box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
				continue;
			}
			if (!living.hurtServer(level, source, damage)) {
				continue;
			}
			level.sendParticles(ParticleTypes.SWEEP_ATTACK,
					(box.minX + box.maxX) / 2.0,
					(box.minY + box.maxY) / 2.0,
					(box.minZ + box.maxZ) / 2.0,
					1, 0.0, 0.0, 0.0, 0.0);
			Vec3 away = living.position().subtract(player.position());
			double length = Math.hypot(away.x, away.z);
			if (length > 1e-6) {
				living.push(away.x / length * KNOCKBACK, 0.15, away.z / length * KNOCKBACK);
			}
			hits++;
		}

		Payloads.broadcast(player, new SwingPayload(player.getId(), SWING_TICKS, ArcSweep.toFloats(arc)));
		return hits;
	}
}
