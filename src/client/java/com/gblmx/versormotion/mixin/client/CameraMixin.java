package com.gblmx.versormotion.mixin.client;

import com.gblmx.versormotion.client.CameraShake;
import com.gblmx.versormotion.client.RollAnimations;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vsr.E3;
import vsr.blaze3d.Blaze3D;

/**
 * Adds local camera roll during a dodge roll and a decaying rotational shake
 * after damage. Both are applied in camera-local space after vanilla has
 * written the render state, so culling and movement are untouched.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/DeltaTracker;)V",
			at = @At("TAIL"))
	private void versormotion$cameraOffset(CameraRenderState state, DeltaTracker tracker, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || state.orientation == null) {
			return;
		}
		float partial = tracker.getGameTimeDeltaPartialTick(false);
		float roll = RollAnimations.cameraRoll(minecraft.player.getId());
		float shake = CameraShake.envelope(minecraft, partial);
		if (shake <= 0f && roll == 0f) {
			return;
		}

		float phase = (minecraft.level.getGameTime() + partial) * 2.7f;
		float pitch = shake * 0.06f * (float) Math.sin(phase);
		roll += shake * 0.05f * (float) Math.sin(phase * 1.7f);

		Quaternionf rotated = new Quaternionf(state.orientation);
		if (pitch != 0f) {
			rotated.mul(Blaze3D.quaternion(E3.rotor(1, 0, 0, pitch)));
		}
		if (roll != 0f) {
			rotated.mul(Blaze3D.quaternion(E3.rotor(0, 0, 1, roll)));
		}
		state.orientation.set(rotated);
	}
}
