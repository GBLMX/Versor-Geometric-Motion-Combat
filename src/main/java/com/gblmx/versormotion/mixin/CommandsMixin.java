package com.gblmx.versormotion.mixin;

import com.gblmx.versormotion.VersorMotion;
import com.gblmx.versormotion.wedit.WeCommands;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bridges the {@code //command} form to the {@code we} command tree. Vanilla
 * strips one leading slash from chat commands, so a player typing {@code //set}
 * reaches {@link Commands#performCommand} as {@code /set}; console and command
 * blocks arrive the same way after {@code trimOptionalPrefix}.
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {
	@Inject(method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V",
			at = @At("HEAD"), cancellable = true)
	private void versormotion$interceptDoubleSlash(ParseResults<CommandSourceStack> results,
			String command, CallbackInfo ci) {
		try {
			if (WeCommands.handle(results.getContext().getSource(), command)) {
				ci.cancel();
			}
		} catch (RuntimeException e) {
			VersorMotion.LOGGER.error("WorldEdit bridge failed for '{}'", command, e);
		}
	}
}
