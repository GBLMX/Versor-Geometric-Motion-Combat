package com.gblmx.versormotion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gblmx.versormotion.combat.ArcSweep;
import com.gblmx.versormotion.combat.CombatRules;
import com.gblmx.versormotion.combat.GuardPlane;
import com.gblmx.versormotion.net.CameraShakePayload;
import com.gblmx.versormotion.net.Payloads;
import com.gblmx.versormotion.net.SwingPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative geometric combat.
 *
 * <p>Every attack is adjudicated from the server's copy of the attacker: the
 * blade arc is baked once from the eye position and yaw, then each nearby
 * living entity whose box intersects the swept arc takes damage. Every direct
 * target of the tick is excluded because vanilla (or a multi-target attack
 * handler) already applies its own hit; the arc is the follow-through that
 * catches the rest of the swing. Clients receive the baked arc and only
 * replay it.
 */
public final class CombatManager {
	private static final Map<UUID, Long> LAST_SWING = new HashMap<>();
	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	/**
	 * Swings queued in the current tick. A multi-target attack handler can call
	 * {@code Player.attack} several times in one tick; deferring the arc to the
	 * end of the tick lets one adjudication exclude every direct target, so no
	 * entity takes both a direct hit and the follow-through arc.
	 */
	private static final class Pending {
		final ServerPlayer player;
		final ServerLevel level;
		final long gameTime;
		final double[] arc;
		final double radius;
		final double eyeX;
		final double eyeY;
		final double eyeZ;
		final float scale;
		final Set<Entity> excluded = new HashSet<>();

		Pending(ServerPlayer player, long gameTime, double[] arc, double radius,
				double eyeX, double eyeY, double eyeZ, float scale) {
			this.player = player;
			this.level = player.level();
			this.gameTime = gameTime;
			this.arc = arc;
			this.radius = radius;
			this.eyeX = eyeX;
			this.eyeY = eyeY;
			this.eyeZ = eyeZ;
			this.scale = scale;
		}
	}

	private CombatManager() {}

	public static void init() {
		AttackEntityCallback.EVENT.register(CombatManager::onAttack);
		ServerTickEvents.END_SERVER_TICK.register(server -> flushSwings());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LAST_SWING.remove(handler.getPlayer().getUUID());
			PENDING.remove(handler.getPlayer().getUUID());
		});
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
			queueSwing(server, target);
		}
		return InteractionResult.PASS;
	}

	/**
	 * Guard parry: the defender must be blocking with a shield (or any item
	 * carrying the {@code blocks_attacks} component, such as a sword under
	 * Sword Blocking Mechanics) and facing the attacker, and the baked blade
	 * arc must cross the guard plane inside shield reach. The hit cancels the
	 * whole attack (no arc damage) and pushes the attacker back.
	 */
	private static boolean parried(ServerPlayer attacker, ServerPlayer defender) {
		CombatRules rules = CombatRules.get();
		if (!rules.parry() || !defender.isBlocking()) {
			return false;
		}
		BlocksAttacks blocks = defender.getUseItem().get(DataComponents.BLOCKS_ATTACKS);
		if (blocks == null) {
			return false;
		}
		DamageSource source = defender.damageSources().playerAttack(attacker);
		if (blocks.bypassedBy().map(set -> set.contains(source.typeHolder())).orElse(false)) {
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
				eye.x, eye.y, eye.z, look.x, look.y, look.z, d, rules.parryReach());
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
		CombatRules rules = CombatRules.get();
		return Math.clamp(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE),
				rules.minReach(), rules.maxReach());
	}

	private static double[] bakeArc(ServerPlayer player) {
		return bakeArc(player, reach(player));
	}

	private static double[] bakeArc(ServerPlayer player, double radius) {
		Vec3 eye = player.getEyePosition();
		return ArcSweep.bake(eye.x, eye.y, eye.z,
				Math.toRadians(player.getYRot()), radius, ArcSweep.SAMPLES);
	}

	/**
	 * Queues this tick's arc. Adjudication is deferred to the end of the tick
	 * so a multi-target attack handler that calls {@code Player.attack} once
	 * per target (Better Combat's reworked sweeping) cannot have its later
	 * targets hit twice: every direct target of the tick is excluded from one
	 * arc, while the arc, reach and attack strength are captured now.
	 */
	private static void queueSwing(ServerPlayer player, Entity target) {
		long now = player.level().getGameTime();
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null || pending.gameTime != now) {
			Vec3 eye = player.getEyePosition();
			double radius = reach(player);
			pending = new Pending(player, now, bakeArc(player, radius), radius,
					eye.x, eye.y, eye.z, player.getAttackStrengthScale(0.5f));
			PENDING.put(player.getUUID(), pending);
		}
		if (target != null) {
			pending.excluded.add(target);
		}
	}

	private static void flushSwings() {
		if (PENDING.isEmpty()) {
			return;
		}
		List<Pending> batch = new ArrayList<>(PENDING.values());
		PENDING.clear();
		for (Pending pending : batch) {
			ServerPlayer player = pending.player;
			if (player.isRemoved() || !player.isAlive() || player.isSpectator()
					|| player.level() != pending.level) {
				continue;
			}
			adjudicate(player, pending.level, pending.arc, pending.radius,
					pending.eyeX, pending.eyeY, pending.eyeZ, pending.scale, pending.excluded);
		}
	}

	/**
	 * Adjudicates one swing and broadcasts its baked arc.
	 *
	 * @param directTarget entity vanilla is about to damage, excluded from the
	 *                     arc to avoid applying two hits to one target
	 * @return number of extra entities the arc damaged
	 */
	public static int swing(ServerPlayer player, Entity directTarget) {
		if (player.isSpectator()) {
			return 0;
		}
		double radius = reach(player);
		double[] arc = bakeArc(player, radius);
		Vec3 eye = player.getEyePosition();
		Set<Entity> excluded = directTarget == null ? Set.of() : Set.of(directTarget);
		return adjudicate(player, player.level(), arc, radius, eye.x, eye.y, eye.z,
				player.getAttackStrengthScale(0.5f), excluded);
	}

	private static int adjudicate(ServerPlayer player, ServerLevel level, double[] arc, double radius,
			double eyeX, double eyeY, double eyeZ, float scale, Set<Entity> excluded) {
		CombatRules rules = CombatRules.get();
		long now = level.getGameTime();
		Long last = LAST_SWING.get(player.getUUID());
		if (last != null && now - last < rules.swingCooldownTicks()) {
			return 0;
		}
		LAST_SWING.put(player.getUUID(), now);

		AABB search = new AABB(
				eyeX - radius - 1.0, eyeY - 2.5, eyeZ - radius - 1.0,
				eyeX + radius + 1.0, eyeY + 2.5, eyeZ + radius + 1.0);
		List<Entity> candidates = level.getEntities(player, search,
				entity -> entity instanceof LivingEntity && !excluded.contains(entity)
						&& entity.isAlive() && !entity.isSpectator());

		float damage = (float) rules.arcDamage() * (0.2f + 0.8f * scale * scale);
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
				double knockback = rules.knockback();
				living.push(away.x / length * knockback, 0.15, away.z / length * knockback);
			}
			hits++;
		}

		Payloads.broadcast(player, new SwingPayload(player.getId(), rules.swingTicks(), ArcSweep.toFloats(arc)));
		return hits;
	}
}
