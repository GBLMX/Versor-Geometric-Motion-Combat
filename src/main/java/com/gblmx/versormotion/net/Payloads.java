package com.gblmx.versormotion.net;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Sends a state transition to the actor and every client tracking it. */
public final class Payloads {
	private Payloads() {}

	public static void broadcast(ServerPlayer source, CustomPacketPayload payload) {
		ServerPlayNetworking.send(source, payload);
		for (ServerPlayer tracker : PlayerLookup.tracking(source)) {
			if (tracker != source) {
				ServerPlayNetworking.send(tracker, payload);
			}
		}
	}

	/** Sends to every player in the level, for effects with no owning entity. */
	public static void broadcast(ServerLevel level, CustomPacketPayload payload) {
		for (ServerPlayer player : level.players()) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
