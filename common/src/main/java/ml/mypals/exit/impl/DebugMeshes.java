package ml.mypals.exit.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import ml.mypals.exit.api.Exit;
import ml.mypals.exit.api.ExitChannel;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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
	private static final Identifier WHITE = ExitMod.id("textures/misc/white.png");
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
		Vec3 camera = ExitRuntime.camera().pos;
		DebugShapes.Stats stats = DebugShapes.stats();
		stats.blockOnScreen = onScreen(blockAt, camera);
		stats.pigOnScreen = onScreen(pigAt.add(0, 0.75, 0), camera);
		channel.portal(() -> {
			stats.meshDraws++;
			draw(block, RenderTypes.solidMovingBlock(), blockAt, camera);
			draw(pig, RenderTypes.entitySolid(WHITE), pigAt, camera);
		});
	}

	private static void build() {
		close();
		Minecraft mc = Minecraft.getInstance();
		Exit.plainFormat(() -> {
			block = upload(DefaultVertexFormat.BLOCK, buffer -> {
				BlockState state = Blocks.CONCRETE.magenta().defaultBlockState();
				PoseStack poseStack = new PoseStack();
				poseStack.translate(-0.5, -0.5, -0.5);
				// From 26.1 there is no renderModel. As BlockFeatureRenderer does, without tint.
				List<BlockStateModelPart> parts = new ArrayList<>();
				mc.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(0), parts);
				QuadInstance instance = new QuadInstance();
				for (BlockStateModelPart part : parts) {
					for (Direction direction : Direction.values()) {
						for (BakedQuad quad : part.getQuads(direction)) {
							buffer.putBakedQuad(poseStack.last(), quad, instance);
						}
					}
					for (BakedQuad quad : part.getQuads(null)) {
						buffer.putBakedQuad(poseStack.last(), quad, instance);
					}
				}
			});
			pig = upload(DefaultVertexFormat.ENTITY, buffer -> {
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
			BufferBuilder buffer = new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);
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
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer indexBuffer = indices.getBuffer(mesh.indexCount());
		Matrix4f modelView = new Matrix4f(ExitRuntime.viewRotation())
			.translate((float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z));
		// Preparing the textures may load them, which cannot happen inside a pass.
		PreparedRenderType prepared = type.prepare();
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(modelView);
		// A pass on the main target, which the portal moves into the channel.
		try (RenderPass pass = FeatureDrawer.openPass("emergency_exit debug mesh", Gpu.mainTarget())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(type.pipeline()));
			pass.setUniform("DynamicTransforms", transform);
			pass.setVertexBuffer(0, mesh.vertices().slice());
			for (PreparedRenderType.Texture texture : prepared.textures()) {
				pass.setUniform(texture.name(), texture.textureView(), texture.sampler());
			}
			pass.setIndexBuffer(indexBuffer, indices.type());
			pass.drawIndexed(mesh.indexCount(), 1, 0, 0, 0);
		}
	}

	/** Where {@code at} shows in the window, as fractions of its size from the top left, or null behind the camera. */
	private static float @Nullable [] onScreen(Vec3 at, Vec3 camera) {
		Vector4f clip = new Vector4f((float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z), 1)
			.mul(ExitRuntime.viewRotation()).mul(ExitRuntime.camera().projectionMatrix);
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
