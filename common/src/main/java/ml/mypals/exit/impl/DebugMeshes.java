package ml.mypals.exit.impl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import ml.mypals.exit.api.Exit;
import ml.mypals.exit.api.ExitChannel;
import ml.mypals.exit.compat.IrisCompat;
import ml.mypals.exit.mixin.CompositeRenderTypeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.function.Consumer;

/**
 * The vertex format check of the debug shapes, above the row of boxes: a magenta concrete block in the block format on
 * the left and a cyan pig in the entity format on the right. Both are built with {@link Exit#plainFormat} into GPU
 * buffers of their own, uploaded with {@link Exit#plainUpload} outside any portal and drawn in a portal on the xray
 * channel, the way a schematic renderer keeps its meshes. A shader pack format leaking into the build shows as
 * scattered triangles instead of the block and the pig. The buffers are made again for each shader pack pipeline.
 * {@code -Demergency_exit.smoke.rawUpload=true} uploads them without plainUpload; on this version plainUpload only runs
 * the upload, so that control looks the same as a normal run.
 */
final class DebugMeshes {
	private static final ResourceLocation WHITE = ExitMod.id("textures/misc/white.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int CYAN = 0xFF20D0D0;

	private static @Nullable Mesh block;
	private static @Nullable Mesh pig;
	/** The pipeline the buffers were uploaded with, so they are made again when it changes. */
	private static @Nullable Object builtFor;
	private static boolean built;

	/** A vertex buffer of quads. There is no VertexBuffer from 1.21.5 on. */
	private record Mesh(GpuBuffer vertices, int indexCount) {}

	private DebugMeshes() {}

	/** Draws the block and the pig on {@code channel}. {@code center} is the middle box, {@code right} the row's right. */
	static void render(ExitChannel channel, Vec3 center, Vec3 right) {
		Object pipeline = IrisCompat.pipeline();
		if (!built || pipeline != builtFor) {
			build();
			builtFor = pipeline;
		}
		Vec3 blockAt = center.subtract(right).add(0, 1.6, 0);
		Vec3 pigAt = center.add(right).add(0, 1.1, 0);
		Vec3 camera = ExitRuntime.camera().getPosition();
		DebugShapes.Stats stats = DebugShapes.stats();
		stats.blockOnScreen = onScreen(blockAt, camera);
		stats.pigOnScreen = onScreen(pigAt.add(0, 0.75, 0), camera);
		channel.portal(() -> {
			stats.meshDraws++;
			draw(block, RenderType.solid(), blockAt, camera);
			draw(pig, RenderType.entitySolid(WHITE), pigAt, camera);
		});
	}

	private static void build() {
		close();
		Minecraft mc = Minecraft.getInstance();
		Exit.plainFormat(() -> {
			block = upload(DefaultVertexFormat.BLOCK, buffer -> {
				BlockState state = Blocks.MAGENTA_CONCRETE.defaultBlockState();
				PoseStack poseStack = new PoseStack();
				poseStack.translate(-0.5, -0.5, -0.5);
				ModelBlockRenderer.renderModel(poseStack.last(), buffer, mc.getBlockRenderer().getBlockModel(state), 1, 1, 1, FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY);
			});
			pig = upload(DefaultVertexFormat.NEW_ENTITY, buffer -> {
				PoseStack poseStack = new PoseStack();
				// As LivingEntityRenderer does: models are upside down, with the feet 1.5 blocks below the origin.
				poseStack.scale(-1, -1, 1);
				poseStack.translate(0, -1.501, 0);
				new PigModel(mc.getEntityModels().bakeLayer(ModelLayers.PIG)).renderToBuffer(poseStack, buffer, FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, CYAN);
			});
		});
		built = true;
		DebugShapes.stats().meshBuilds++;
	}

	private static Mesh upload(VertexFormat format, Consumer<BufferBuilder> fill) {
		try (ByteBufferBuilder bytes = new ByteBufferBuilder(format.getVertexSize() * 4 * 256)) {
			BufferBuilder buffer = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
			fill.accept(buffer);
			try (MeshData mesh = buffer.buildOrThrow()) {
				GpuBuffer[] vertices = new GpuBuffer[1];
				Runnable upload = () -> vertices[0] = RenderSystem.getDevice().createBuffer(() -> "emergency_exit debug mesh",
					GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
				if (Boolean.getBoolean("emergency_exit.smoke.rawUpload")) {
					upload.run();
				} else {
					Exit.plainUpload(upload);
				}
				return new Mesh(vertices[0], mesh.drawState().indexCount());
			}
		}
	}

	private static void draw(@Nullable Mesh mesh, RenderType type, Vec3 at, Vec3 camera) {
		if (mesh == null) {
			return;
		}
		// Getting the index buffer may upload it, which cannot happen inside a pass.
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		GpuBuffer indexBuffer = indices.getBuffer(mesh.indexCount());
		Matrix4f modelView = new Matrix4f(ExitRuntime.viewRotation())
			.translate((float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z));
		type.setupRenderState();
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1, 1, 1, 1),
			RenderSystem.getModelOffset(), RenderSystem.getTextureMatrix(), RenderSystem.getShaderLineWidth());
		// A pass on the main target, which the portal moves into the channel.
		try (RenderPass pass = BatchDrawer.openPass("emergency_exit debug mesh", Gpu.mainTarget())) {
			pass.setPipeline(((CompositeRenderTypeAccessor) type).emergency_exit$renderPipeline());
			pass.setUniform("DynamicTransforms", transform);
			pass.setVertexBuffer(0, mesh.vertices());
			for (int i = 0; i < 12; i++) {
				GpuTextureView texture = RenderSystem.getShaderTexture(i);
				if (texture != null) {
					pass.bindSampler("Sampler" + i, texture);
				}
			}
			pass.setIndexBuffer(indexBuffer, indices.type());
			pass.drawIndexed(0, 0, mesh.indexCount(), 1);
		} finally {
			type.clearRenderState();
		}
	}

	/** Where {@code at} shows in the window, as fractions of its size from the top left, or null behind the camera. */
	private static float @Nullable [] onScreen(Vec3 at, Vec3 camera) {
		Vector4f clip = new Vector4f((float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z), 1)
			.mul(ExitRuntime.viewRotation()).mul(ExitRuntime.projectionMatrix());
		if (clip.w <= 0) {
			return null;
		}
		return new float[] {(clip.x / clip.w + 1) / 2, (1 - clip.y / clip.w) / 2};
	}

	/** Frees the buffers. They are made again when the shapes are drawn next. */
	static void close() {
		if (block != null) {
			block.vertices().close();
			block = null;
		}
		if (pig != null) {
			pig.vertices().close();
			pig = null;
		}
		built = false;
	}
}
