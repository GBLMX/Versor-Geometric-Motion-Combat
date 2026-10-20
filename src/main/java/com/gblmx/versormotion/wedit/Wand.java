package com.gblmx.versormotion.wedit;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Wooden axe marked with custom data; left click sets pos1, right click sets pos2. */
public final class Wand {
	private static final String TAG = "versormotion_wand";

	private Wand() {}

	public static ItemStack create() {
		ItemStack stack = new ItemStack(Items.WOODEN_AXE);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(TAG, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal("Versor Wand").withStyle(ChatFormatting.AQUA));
		return stack;
	}

	public static boolean isWand(ItemStack stack) {
		if (!stack.is(Items.WOODEN_AXE)) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(TAG, false);
	}

	public static void init() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (!(player instanceof ServerPlayer server) || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (!isWand(server.getItemInHand(hand)) || !server.permissions()
					.hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
				return InteractionResult.PASS;
			}
			Selection.setCorner(server, 1, pos);
			server.sendSystemMessage(Component.literal("pos1 = " + pos.toShortString()));
			return InteractionResult.SUCCESS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (!(player instanceof ServerPlayer server) || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (!isWand(server.getItemInHand(hand)) || !server.permissions()
					.hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
				return InteractionResult.PASS;
			}
			var pos = hitResult.getBlockPos();
			Selection.setCorner(server, 2, pos);
			server.sendSystemMessage(Component.literal("pos2 = " + pos.toShortString()));
			return InteractionResult.SUCCESS;
		});
	}
}
