package com.gblmx.versormotion;

import com.gblmx.versormotion.mixin.DisplayAccessor;
import com.mojang.math.Transformation;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import vsr.Multivector;
import vsr.blaze3d.Blaze3D;

/**
 * Read/modify {@link Display}'s full transformation without dropping either
 * rotation field. Mojang composes it as {@code T * left_rotation * scale *
 * right_rotation}; a rigid versor only replaces one side at a time.
 */
public final class DisplayTransforms {
	private DisplayTransforms() {}

	public static Transformation get(Display display) {
		SynchedEntityData data = display.getEntityData();
		return new Transformation(
				data.get(DisplayAccessor.versormotion$translation()),
				data.get(DisplayAccessor.versormotion$leftRotation()),
				data.get(DisplayAccessor.versormotion$scale()),
				data.get(DisplayAccessor.versormotion$rightRotation()));
	}

	public static void set(Display display, Transformation transformation) {
		display.setTransformation(transformation);
	}

	/** Exact display matrix, including non-uniform scale and both rotations. */
	public static Matrix4f matrix(Display display) {
		return get(display).getMatrixCopy();
	}

	/** World-space rotor baked into {@code left_rotation}. */
	public static Multivector leftRotor(Display display) {
		return Blaze3D.rotor(new Quaternionf(get(display).leftRotation()));
	}

	/** Local-space rotor baked into {@code right_rotation}. */
	public static Multivector rightRotor(Display display) {
		return Blaze3D.rotor(new Quaternionf(get(display).rightRotation()));
	}

	/**
	 * Rotate about the display's own origin while preserving translation,
	 * scale and the other rotation. {@code local} writes right_rotation
	 * instead of left_rotation.
	 */
	public static void rotate(Display display, Multivector rotor, boolean local) {
		if (local) {
			rotateRight(display, rotor);
		} else {
			rotateLeft(display, rotor);
		}
	}

	/** {@code left' = rotor * left}, i.e. a world-space rotation. */
	public static void rotateLeft(Display display, Multivector rotor) {
		Transformation t = get(display);
		Quaternionf left = new Quaternionf(Blaze3D.quaternion(rotor))
				.mul(t.leftRotation())
				.normalize();
		set(display, new Transformation(t.translation(), left, t.scale(), t.rightRotation()));
	}

	/** {@code right' = right * rotor}, i.e. a local-space rotation. */
	public static void rotateRight(Display display, Multivector rotor) {
		Transformation t = get(display);
		Quaternionf right = new Quaternionf(t.rightRotation())
				.mul(Blaze3D.quaternion(rotor))
				.normalize();
		set(display, new Transformation(t.translation(), t.leftRotation(), t.scale(), right));
	}

	public static void setLeftRotation(Display display, Quaternionfc rotation) {
		Transformation t = get(display);
		set(display, new Transformation(t.translation(), new Quaternionf(rotation),
				t.scale(), t.rightRotation()));
	}

	public static void setRightRotation(Display display, Quaternionfc rotation) {
		Transformation t = get(display);
		set(display, new Transformation(t.translation(), t.leftRotation(),
				t.scale(), new Quaternionf(rotation)));
	}
}
