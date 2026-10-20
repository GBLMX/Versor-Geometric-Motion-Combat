package com.gblmx.versormotion;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Particle types shared by both sides. The texture and the client renderer
 * live under {@code assets/versormotion} and the client source set.
 */
public final class VersorParticles {
	public static final SimpleParticleType SWING_SPARK = FabricParticleTypes.simple();

	private static boolean registered;

	private VersorParticles() {}

	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		Registry.register(BuiltInRegistries.PARTICLE_TYPE,
				Identifier.fromNamespaceAndPath(VersorMotion.MOD_ID, "swing_spark"),
				SWING_SPARK);
	}
}
