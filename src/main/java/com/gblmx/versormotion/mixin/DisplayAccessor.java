package com.gblmx.versormotion.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the four synched-data entries behind {@code Display}'s
 * {@code transformation}. Vanilla only has a setter, so reading the current
 * left/right rotations would otherwise require a copy through NBT.
 */
@Mixin(Display.class)
public interface DisplayAccessor {
	@Accessor("DATA_TRANSLATION_ID")
	static EntityDataAccessor<Vector3fc> versormotion$translation() {
		throw new AssertionError();
	}

	@Accessor("DATA_LEFT_ROTATION_ID")
	static EntityDataAccessor<Quaternionfc> versormotion$leftRotation() {
		throw new AssertionError();
	}

	@Accessor("DATA_SCALE_ID")
	static EntityDataAccessor<Vector3fc> versormotion$scale() {
		throw new AssertionError();
	}

	@Accessor("DATA_RIGHT_ROTATION_ID")
	static EntityDataAccessor<Quaternionfc> versormotion$rightRotation() {
		throw new AssertionError();
	}
}
