package com.gblmx.versormotion.mixin.client;

import com.gblmx.versormotion.client.RollAnimations;
import com.gblmx.versormotion.client.TrailAnimations;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vsr.E3;
import vsr.blaze3d.Blaze3D;

/**
 * Root somersault. Runs after vanilla yaw/pose rotations, so the rotation
 * axis is the player's local right axis; the pivot is the body centre.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(
			method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
			at = @At("TAIL"))
	private void versormotion$roll(AvatarRenderState state, PoseStack poseStack, float f, float g, CallbackInfo ci) {
		float eased = RollAnimations.eased(state.id);
		if (eased >= 0f) {
			poseStack.translate(0.0, 0.9, 0.0);
			poseStack.mulPose(new Matrix4f().rotationX((float) Math.toRadians(-360.0 * eased)));
			poseStack.translate(0.0, -0.9, 0.0);
		}
		float twist = TrailAnimations.twist(state.id);
		if (twist != 0f) {
			poseStack.mulPose(new Matrix4f().rotation(Blaze3D.quaternion(E3.rotor(0, 1, 0, twist))));
		}
	}
}
