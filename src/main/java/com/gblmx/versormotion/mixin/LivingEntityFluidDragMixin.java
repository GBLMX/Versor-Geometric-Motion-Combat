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
		return FluidDrag.applyWater((LivingEntity) (Object) this, velocity, velocity.multiply(x, y, z));
	}
}
