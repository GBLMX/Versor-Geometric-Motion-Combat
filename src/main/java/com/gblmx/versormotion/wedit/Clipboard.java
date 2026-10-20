package com.gblmx.versormotion.wedit;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Player clipboard: a cuboid of states plus the origin used to paste it. */
public record Clipboard(ResourceKey<Level> dimension, BlockPos min, BlockPos origin,
		int sizeX, int sizeY, int sizeZ, BlockState[] states) {
	public Clipboard {
		if (states.length != sizeX * sizeY * sizeZ) {
			throw new IllegalArgumentException("state count does not match the clipboard size");
		}
	}

	public int index(int x, int y, int z) {
		return (y * sizeZ + z) * sizeX + x;
	}

	public long volume() {
		return (long) sizeX * sizeY * sizeZ;
	}
}
