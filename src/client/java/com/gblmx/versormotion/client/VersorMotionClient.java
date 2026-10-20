package com.gblmx.versormotion.client;

import java.util.Optional;
import java.util.OptionalDouble;

import com.gblmx.versormotion.VersorMotion;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import vsr.E3;
import vsr.Field;
import vsr.Motion;
import vsr.blaze3d.Blaze3D;

/**
 * Renders a cube whose orientation comes from a PGA motor, using the
 * extraction/drawing split of the 26.x render system.
 */
public final class VersorMotionClient implements ClientModInitializer {
	private static final RenderPipeline CUBE_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(VersorMotion.MOD_ID, "pipeline/versor_cube"))
					.withDepthStencilState(Optional.empty())
					.build());

	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static final StagedVertexBuffer BUFFER =
			new StagedVertexBuffer(() -> "Versor field", 1 << 16);

	private static final Field SWIRL = (x, y, z) -> E3.vec(-y, x, 0);
	private static final int SEEDS = 5;
	private static final int STEPS = 16;
	private static final double STEP_LENGTH = 0.035;

	private CubeState state = CubeState.HIDDEN;

	@Override
	public void onInitializeClient() {
		LevelExtractionEvents.END_EXTRACTION.register(this::extract);
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::render);
	}

	private void extract(LevelExtractionContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			state = CubeState.HIDDEN;
			return;
		}

		double seconds = (System.nanoTime() % 60_000_000_000L) / 1e9;
		Motion spin = Motion.rotation(0, 1, 0, 1.2);
		Matrix4f motor = Blaze3D.matrix(spin.motor(seconds));

		Vec3 eye = client.player.getEyePosition();
		Vec3 center = eye.add(client.player.getLookAngle().scale(3.0));

		float[] dots = new float[SEEDS * STEPS * 3];
		int index = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			double angle = seed * 2 * Math.PI / SEEDS;
			double px = 2.0 * Math.cos(angle);
			double py = 2.0 * Math.sin(angle);
			double pz = 0;
			for (int step = 0; step < STEPS; step++) {
				vsr.Multivector v = SWIRL.at(px, py, pz);
				px += v.get(1) * STEP_LENGTH;
				py += v.get(2) * STEP_LENGTH;
				pz += v.get(4) * STEP_LENGTH;
				dots[index++] = (float) (center.x + px);
				dots[index++] = (float) (center.y + py);
				dots[index++] = (float) (center.z + pz);
			}
		}

		state = new CubeState(motor, dots, (float) center.x, (float) center.y, (float) center.z);
	}

	private void render(LevelRenderContext context) {
		if (!state.visible()) {
			return;
		}

		VertexFormat format = CUBE_PIPELINE.getVertexFormatBinding(0);
		PrimitiveTopology primitive = CUBE_PIPELINE.getPrimitiveTopology();
		StagedVertexBuffer.Draw draw = BUFFER.appendDraw(format, primitive,
				primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);

		PoseStack pose = context.poseStack();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		pose.pushPose();
		pose.translate(-camera.x, -camera.y, -camera.z);

		Matrix4f model = new Matrix4f(pose.last().pose());
		model.translate(state.x(), state.y(), state.z());
		model.mul(state.motor());

		VertexConsumer builder = BUFFER.getVertexBuilder(draw);
		renderBox(model, builder, -0.75f, -0.75f, -0.75f, 0.75f, 0.75f, 0.75f,
				0.35f, 0.8f, 1.0f, 0.35f);

		float[] dots = state.dots();
		for (int i = 0; i + 2 < dots.length; i += 3) {
			Matrix4f dot = new Matrix4f(pose.last().pose());
			dot.translate(dots[i], dots[i + 1], dots[i + 2]);
			renderBox(dot, builder, -0.035f, -0.035f, -0.035f, 0.035f, 0.035f, 0.035f,
					1.0f, 0.55f, 0.2f, 0.85f);
		}

		pose.popPose();

		BUFFER.upload();
		StagedVertexBuffer.ExecuteInfo info = BUFFER.getExecuteInfo(draw);
		if (info != null) {
			draw(Minecraft.getInstance(), info, CUBE_PIPELINE);
		}
		BUFFER.endFrame();
	}

	private static void renderBox(Matrix4fc matrix, VertexConsumer buffer,
			float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
			float red, float green, float blue, float alpha) {
		buffer.addVertex(matrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);

		buffer.addVertex(matrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, minX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, minX, maxY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);

		buffer.addVertex(matrix, minX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, minX, maxY, minZ).setColor(red, green, blue, alpha);

		buffer.addVertex(matrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);

		buffer.addVertex(matrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, minX, maxY, minZ).setColor(red, green, blue, alpha);

		buffer.addVertex(matrix, minX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
	}

	private static void draw(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
		GpuTextureView colorTexture = mainTarget.getColorTextureView();
		assert colorTexture != null;

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> VersorMotion.MOD_ID + " versor cube",
						colorTexture, Optional.empty(), mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
			renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);
			renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
			renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());
			renderPass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
		}
	}

	public static void close() {
		BUFFER.close();
	}

	private record CubeState(Matrix4f motor, float[] dots, float x, float y, float z) {
		static final CubeState HIDDEN = new CubeState(null, new float[0], 0, 0, 0);

		boolean visible() {
			return motor != null;
		}
	}
}
