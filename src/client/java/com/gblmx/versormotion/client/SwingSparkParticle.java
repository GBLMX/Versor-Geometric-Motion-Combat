package com.gblmx.versormotion.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Cyan spark used for the baked swing trail. It is emitted client-side from
 * the server's arc samples, so it has no physics of its own: it fades out at
 * the sample the server adjudicated.
 */
final class SwingSparkParticle extends SingleQuadParticle {
	private SwingSparkParticle(ClientLevel level, double x, double y, double z,
			double xd, double yd, double zd, TextureAtlasSprite sprite) {
		super(level, x, y, z, xd, yd, zd, sprite);
		this.lifetime = 8 + random.nextInt(5);
		this.quadSize = 0.09f + random.nextFloat() * 0.05f;
		this.gravity = 0.0f;
		this.friction = 0.92f;
		this.rCol = 0.55f;
		this.gCol = 0.88f;
		this.bCol = 1.0f;
		this.alpha = 1.0f;
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	public ParticleRenderType getGroup() {
		return ParticleRenderType.SINGLE_QUADS;
	}

	static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
		return (type, level, x, y, z, xd, yd, zd, random) ->
				new SwingSparkParticle(level, x, y, z, xd, yd, zd, sprites.get(random));
	}
}
