package com.gblmx.versormotion.mixin.client;

import com.gblmx.versormotion.client.RollAnimations;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tucks limbs in when the player is inside the roll. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(
			method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
			at = @At("TAIL"))
	private void versormotion$rollTuck(AvatarRenderState state, CallbackInfo ci) {
		float weight = RollAnimations.tuck(state.id);
		if (weight <= 0f) {
			return;
		}
		PlayerModel model = (PlayerModel) (Object) this;
		model.rightLeg.xRot = Mth.lerp(weight, model.rightLeg.xRot, -1.4f);
		model.leftLeg.xRot = Mth.lerp(weight, model.leftLeg.xRot, -1.4f);
		model.rightArm.xRot = Mth.lerp(weight, model.rightArm.xRot, -2.3f);
		model.leftArm.xRot = Mth.lerp(weight, model.leftArm.xRot, -2.3f);
		model.head.xRot = Mth.lerp(weight, model.head.xRot, -0.5f);
	}
}
