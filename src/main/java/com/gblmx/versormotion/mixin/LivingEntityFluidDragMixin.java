package com.gblmx.versormotion.mixin;

import com.gblmx.versormotion.FluidDrag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Swaps vanilla's fixed per-tick water damping for quadratic drag.
 * The redirected call is {@code movement.multiply(slowDown, 0.8, slowDown)}
 * at the end of {@code LivingEntity.travelInWater}; everything before it
 * (thrust, collision move) and after it (gravity adjustment, jump) is vanilla.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFluidDragMixin {
	@Redirect(
			method = "travelInWater(Lnet/minecraft/world/phys/Vec3;DZD)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 versormotion$quadraticDrag(Vec3 velocity, double x, double y, double z) {
		return FluidDrag.applyWater((LivingEntity) (Object) this, velocity, velocity.multiply(x, y, z), x);
	}

	@Redirect(
			method = "travelInWater(Lnet/minecraft/world/phys/Vec3;DZD)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;getFluidFallingAdjustedMovement(DZLnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 versormotion$waterGravity(LivingEntity self, double baseGravity, boolean isFalling, Vec3 movement) {
		return FluidDrag.gravityStep(self, movement, baseGravity);
	}

	@Redirect(
			method = "travelInAir(Lnet/minecraft/world/phys/Vec3;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;getEffectiveGravity()D"))
	private double versormotion$airGravity(LivingEntity self) {
		// Scales slow falling (0.01) with the rest, so the relative effect survives.
		return ((LivingEntityAccessor) self).versormotion$effectiveGravity() * FluidDrag.GRAVITY_SCALE;
	}

	@Redirect(
			method = "jumpFromGround()V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;getJumpPower()F"))
	private float versormotion$jumpPower(LivingEntity self) {
		return ((LivingEntityAccessor) self).versormotion$jumpPower() * FluidDrag.JUMP_SCALE;
	}
}
