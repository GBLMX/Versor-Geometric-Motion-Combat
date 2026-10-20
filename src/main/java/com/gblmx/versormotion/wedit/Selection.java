package com.gblmx.versormotion.wedit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Per-player cuboid selection. Corners reset when the player changes dimension. */
public final class Selection {
	/** Inclusive cuboid from two corners. */
	public record Box(ResourceKey<Level> dimension, BlockPos a, BlockPos b) {
		public int minX() {
			return Math.min(a.getX(), b.getX());
		}

		public int minY() {
			return Math.min(a.getY(), b.getY());
		}

		public int minZ() {
			return Math.min(a.getZ(), b.getZ());
		}

		public int maxX() {
			return Math.max(a.getX(), b.getX());
		}

		public int maxY() {
			return Math.max(a.getY(), b.getY());
		}

		public int maxZ() {
			return Math.max(a.getZ(), b.getZ());
		}

		public int sizeX() {
			return maxX() - minX() + 1;
		}

		public int sizeY() {
			return maxY() - minY() + 1;
		}

		public int sizeZ() {
			return maxZ() - minZ() + 1;
		}

		public long volume() {
			return (long) sizeX() * sizeY() * sizeZ();
		}

		public BlockPos min() {
			return new BlockPos(minX(), minY(), minZ());
		}

		public BlockPos max() {
			return new BlockPos(maxX(), maxY(), maxZ());
		}

		public BlockPos center() {
			return new BlockPos(Math.floorDiv(minX() + maxX(), 2), Math.floorDiv(minY() + maxY(), 2),
					Math.floorDiv(minZ() + maxZ(), 2));
		}
	}

	private record Corners(ResourceKey<Level> dimension, BlockPos first, BlockPos second) {}

	private static final Map<UUID, Corners> CORNERS = new HashMap<>();

	private Selection() {}

	public static void setCorner(ServerPlayer player, int corner, BlockPos pos) {
		ResourceKey<Level> dimension = player.level().dimension();
		Corners current = CORNERS.get(player.getUUID());
		if (current == null || !current.dimension().equals(dimension)) {
			current = new Corners(dimension, null, null);
		}
		CORNERS.put(player.getUUID(), new Corners(dimension,
				corner == 1 ? pos : current.first(),
				corner == 2 ? pos : current.second()));
	}

	/** @return the selection, or null when either corner is missing or dimensions differ */
	public static Box get(ServerPlayer player) {
		Corners corners = CORNERS.get(player.getUUID());
		if (corners == null || corners.first() == null || corners.second() == null
				|| !corners.dimension().equals(player.level().dimension())) {
			return null;
		}
		return new Box(corners.dimension(), corners.first(), corners.second());
	}

	public static void clear(UUID player) {
		CORNERS.remove(player);
	}
}
