package com.gblmx.versormotion;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Quadratic hydrodynamic drag for living entities moving through a liquid.
 *
 * <p>Vanilla damps water velocity with a fixed linear factor per tick
 * (0.8, 0.9 while sprinting, 0.96 with Dolphin's Grace). This replaces that
 * multiply with an implicit step of {@code dv/dt = -k |v| v}, where
 * {@code k = 0.5 rho Cd A / m} comes from the fluid density, the entity's
 * box cross-section and its mass. Only the drag step changes: swim thrust,
 * gravity and the jump-out-of-fluid adjustment stay vanilla.
 *
 * <p>With 1 block = 1 m and one tick as the time step, the 20 tps / 400
 * conversion cancels, so velocities stay in blocks per tick.
 */
public final class FluidDrag {
	public static final double WATER_DENSITY = 1000.0;
	public static final double DRAG_COEFFICIENT = 1.1;
	/** Real gravity 10 m/s^2 at 1 block = 1 m and 20 ticks/s. */
	public static final double GRAVITY_PER_TICK = 10.0 / 400.0;
	/** Vanilla air gravity, used to keep slow-falling proportional. */
	public static final double VANILLA_GRAVITY = 0.08;
	public static final double GRAVITY_SCALE = GRAVITY_PER_TICK / VANILLA_GRAVITY;
	/** 0.42 -> 0.252 keeps the vanilla jump apex at ~1.25 blocks under 0.025 g. */
	public static final float JUMP_SCALE = 0.6f;

	private FluidDrag() {}

	/**
	 * @param velocity post-move velocity, blocks/tick
	 * @param vanilla  what the vanilla multiply would have produced, used when no water is present
	 * @param submerged fraction of the entity box under the fluid surface, 0..1
	 */
	public static Vec3 apply(Vec3 velocity, Vec3 vanilla, double density,
			double width, double height, double submerged, double dragScale) {
		double speed = velocity.length();
		if (speed < 1e-9 || submerged <= 0 || width <= 1e-9 || height <= 1e-9) {
			return vanilla;
		}
		double mass = WATER_DENSITY * width * width * height;
		double k = 0.5 * density * DRAG_COEFFICIENT * (width * height) / mass * submerged;
		double horizontal = k * dragScale;
		return new Vec3(
				velocity.x / (1.0 + horizontal * speed),
				velocity.y / (1.0 + k * speed),
				velocity.z / (1.0 + horizontal * speed));
	}

	public static Vec3 applyWater(LivingEntity entity, Vec3 velocity, Vec3 vanilla, double slowDown) {
		// Bubble columns are a scripted lift, not free-stream drag; keep their
		// vanilla damping so soul-sand elevators and magma sinks behave as designed.
		if (inBubbleColumn(entity)) {
			return vanilla;
		}
		double height = entity.getBbHeight();
		if (height <= 1e-9) {
			return vanilla;
		}
		double fluidHeight = entity.getFluidHeight(FluidTags.WATER);
		double submerged = Math.min(1.0, Math.max(0.0, fluidHeight / height));
		// Vanilla encodes buffs as less linear damping: 0.8 baseline, 0.9 sprinting,
		// 0.96 Dolphin's Grace. Keep that relative benefit in the quadratic model.
		double dragScale = Math.max(0.05, (1.0 - slowDown) / 0.2);
		return apply(velocity, vanilla, WATER_DENSITY, entity.getBbWidth(), height, submerged, dragScale);
	}

	/**
	 * Replaces vanilla's {@code -baseGravity/16} fudge with real gravity and
	 * neutral buoyancy: the submerged fraction cancels that share of it.
	 * Bubble columns keep their scripted vanilla behaviour.
	 */
	public static Vec3 gravityStep(LivingEntity entity, Vec3 movement, double baseGravity) {
		if (inBubbleColumn(entity)) {
			return entity.getFluidFallingAdjustedMovement(baseGravity, movement.y <= 0.0, movement);
		}
		double height = entity.getBbHeight();
		double fluidHeight = entity.getFluidHeight(FluidTags.WATER);
		double submerged = height > 1e-9 ? Math.min(1.0, Math.max(0.0, fluidHeight / height)) : 0.0;
		double net = -GRAVITY_PER_TICK * (1.0 - submerged);
		return new Vec3(movement.x, movement.y + net, movement.z);
	}

	private static boolean inBubbleColumn(LivingEntity entity) {
		if (entity.level().getBlockState(entity.blockPosition()).is(Blocks.BUBBLE_COLUMN)) {
			return true;
		}
		BlockPos eye = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
		return entity.level().getBlockState(eye).is(Blocks.BUBBLE_COLUMN);
	}
}
