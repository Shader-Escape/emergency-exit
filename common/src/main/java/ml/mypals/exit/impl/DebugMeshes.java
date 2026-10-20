package ml.mypals.exit.impl;

import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import ml.mypals.exit.api.Exit;
import ml.mypals.exit.api.ExitChannel;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
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
 * the left and a cyan pig in the entity format on the right. Both are built with {@link Exit#plainFormat} into vertex
 * buffers of their own, uploaded with {@link Exit#plainUpload} outside any portal and drawn in a portal on the xray
 * channel, the way a schematic renderer keeps its meshes. Iris 1.8 bakes its widened layout into a buffer uploaded
 * anywhere else, which shows as scattered triangles instead of the block and the pig. The buffers are made again for
 * each shader pack pipeline, so the upload sees the pack in use. {@code -Demergency_exit.smoke.rawUpload=true} uploads
 * them without plainUpload, as a control run that should fail.
 */
final class DebugMeshes {
	private static final ResourceLocation WHITE = ExitMod.id("textures/misc/white.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int CYAN = 0xFF20D0D0;

	private static @Nullable VertexBuffer block;
	private static @Nullable VertexBuffer pig;
	/** The pipeline the buffers were uploaded with, so they are made again when it changes. */
	private static @Nullable Object builtFor;
	private static boolean built;

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
			BatchDrawer.executeInto(Minecraft.getInstance().getMainRenderTarget(), () -> {
				draw(block, RenderType.solid(), blockAt, camera);
				draw(pig, RenderType.entitySolid(WHITE), pigAt, camera);
			});
		});
	}

	private static void build() {
		close();
		Minecraft mc = Minecraft.getInstance();
		Exit.plainFormat(() -> {
			block = upload(DefaultVertexFormat.BLOCK, buffer -> {
				BlockRenderDispatcher blocks = mc.getBlockRenderer();
				BlockState state = Blocks.MAGENTA_CONCRETE.defaultBlockState();
				PoseStack poseStack = new PoseStack();
				poseStack.translate(-0.5, -0.5, -0.5);
				blocks.getModelRenderer().renderModel(poseStack.last(), buffer, state, blocks.getBlockModel(state), 1, 1, 1, FULL_BRIGHT,
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

	private static VertexBuffer upload(VertexFormat format, Consumer<BufferBuilder> fill) {
		try (ByteBufferBuilder bytes = new ByteBufferBuilder(format.getVertexSize() * 4 * 256)) {
			BufferBuilder buffer = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
			fill.accept(buffer);
			VertexBuffer vertexBuffer = new VertexBuffer(BufferUsage.STATIC_WRITE);
			Runnable upload = () -> {
				vertexBuffer.bind();
				vertexBuffer.upload(buffer.buildOrThrow());
				VertexBuffer.unbind();
			};
			if (Boolean.getBoolean("emergency_exit.smoke.rawUpload")) {
				upload.run();
			} else {
				Exit.plainUpload(upload);
			}
			return vertexBuffer;
		}
	}

	private static void draw(@Nullable VertexBuffer buffer, RenderType type, Vec3 at, Vec3 camera) {
		if (buffer == null) {
			return;
		}
		Matrix4f modelView = new Matrix4f(ExitRuntime.viewRotation())
			.translate((float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z));
		type.setupRenderState();
		buffer.bind();
		buffer.drawWithShader(modelView, new Matrix4f(ExitRuntime.projectionMatrix()), RenderSystem.getShader());
		VertexBuffer.unbind();
		type.clearRenderState();
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
			block.close();
			block = null;
		}
		if (pig != null) {
			pig.close();
			pig = null;
		}
		built = false;
	}
}
