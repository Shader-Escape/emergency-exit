package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.Minecraft;
import ml.mypals.exit.mixin.FeatureRenderDispatcherAccessor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4fStack;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Renders submit storages into a target with our own feature dispatcher. It sets this frame's camera rotation and
 * projection itself, because the level renderer may have reset them by the time EXIT renders. Before 26.2 the features
 * are drawn into a buffer source, and each batch opens its own pass on the output target when it ends, so the output
 * is pointed at the target while the features are drawn. In 1.21.11 the dispatcher draws all features at once and then
 * empties the storage; {@link #keepSubmits} keeps them, so they are drawn twice: the solid render types first, then
 * the translucent ones (its custom geometry is kept in a hash map, so the order of render types is not fixed). The
 * translucent depth draws the translucent ones a third time.
 */
public final class FeatureDrawer {
	private static RenderBuffers buffers;
	private static FeatureRenderDispatcher dispatcher;
	private static PhasedBufferSource bufferSource;
	/** While true, the dispatcher keeps the submits after drawing them, see FeatureRenderDispatcherMixin. */
	public static boolean keepSubmits;

	private FeatureDrawer() {}

	static SubmitNodeStorage newStorage() {
		return new SubmitNodeStorage();
	}

	/** Renders {@code storage} into {@code target} and empties it. */
	static void draw(SubmitNodeStorage storage, RenderTarget target) {
		draw(storage, target, null);
	}

	/**
	 * Renders {@code storage} into {@code target} and empties it. {@code direct} draws into the buffer source after the
	 * translucent features, for what cannot be submitted before 26.2, such as gizmos.
	 */
	static void draw(SubmitNodeStorage storage, RenderTarget target, @Nullable Consumer<MultiBufferSource> direct) {
		FeatureRenderDispatcher dispatcher = dispatcher();
		((FeatureRenderDispatcherAccessor) dispatcher).emergency_exit$setSubmitNodeStorage(storage);
		// The vertices are written while the features are drawn. Iris would widen their format while the level renders,
		// but the vanilla pipelines expect the vanilla format.
		boolean renderingLevel = IrisCompat.isRenderingLevel();
		IrisCompat.setRenderingLevel(false);
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.set(ExitRuntime.viewRotation());
		try {
			long opened = ExitRuntime.openedPasses();
			executeInto(Objects.requireNonNull(target.getColorTextureView(), "target without colour"), target.getDepthTextureView(), () -> {
				render(dispatcher, false);
				render(dispatcher, true);
				buffers.outlineBufferSource().endOutlineBatch();
				if (direct != null) {
					direct.accept(bufferSource);
					bufferSource.endBatch();
				}
			});
			// Inside a portal a pass on the main target was moved into the channel's target.
			Channel channel = ExitRuntime.routingChannel();
			if (ExitRuntime.openedPasses() != opened && channel != null && (target == Gpu.mainTarget() || target == channel.drawnTarget())) {
				TranslucentDepth.record(() -> render(dispatcher, true), channel.prepareForDraw(), channel.translucentDepth());
			}
		} finally {
			modelView.popMatrix();
			IrisCompat.setRenderingLevel(renderingLevel);
			storage.clear();
		}
	}

	/** Draws the submits of the solid or of the translucent render types and keeps the submits. */
	private static void render(FeatureRenderDispatcher dispatcher, boolean translucent) {
		keepSubmits = true;
		bufferSource.translucent = translucent;
		try {
			dispatcher.renderAllFeatures();
		} finally {
			keepSubmits = false;
			bufferSource.translucent = null;
		}
		bufferSource.endBatch();
		buffers.crumblingBufferSource().endBatch();
	}

	/**
	 * Runs {@code execute} with the output of the features pointed at the given colour and depth, and with the level
	 * projection set, since each feature binds the default uniforms itself.
	 */
	static void executeInto(GpuTextureView color, @Nullable GpuTextureView depth, Runnable execute) {
		GpuTextureView savedColor = RenderSystem.outputColorTextureOverride;
		GpuTextureView savedDepth = RenderSystem.outputDepthTextureOverride;
		GpuBufferSlice projection = ExitRuntime.levelProjection();
		GpuBufferSlice current = RenderSystem.getProjectionMatrixBuffer();
		var type = RenderSystem.getProjectionType();
		RenderSystem.outputColorTextureOverride = color;
		RenderSystem.outputDepthTextureOverride = depth;
		if (projection != null && projection != current) {
			RenderSystem.setProjectionMatrix(projection, type);
		}
		try {
			execute.run();
		} finally {
			RenderSystem.outputColorTextureOverride = savedColor;
			RenderSystem.outputDepthTextureOverride = savedDepth;
			if (projection != null && projection != current && current != null) {
				RenderSystem.setProjectionMatrix(current, type);
			}
		}
	}

	/** Opens a pass on {@code target} with the default uniforms and the level projection bound. */
	static RenderPass openPass(String label, RenderTarget target) {
		return openPass(label, Objects.requireNonNull(target.getColorTextureView(), "target without colour"), target.getDepthTextureView());
	}

	/** Opens a pass on the given colour and depth with the default uniforms and the level projection bound. */
	static RenderPass openPass(String label, GpuTextureView color, @Nullable GpuTextureView depth) {
		RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> label, color, OptionalInt.empty(), depth, OptionalDouble.empty());
		GpuBufferSlice projection = ExitRuntime.levelProjection();
		GpuBufferSlice current = RenderSystem.getProjectionMatrixBuffer();
		if (projection != null && projection != current) {
			var type = RenderSystem.getProjectionType();
			RenderSystem.setProjectionMatrix(projection, type);
			try {
				RenderSystem.bindDefaultUniforms(pass);
			} finally {
				if (current != null) {
					RenderSystem.setProjectionMatrix(current, type);
				}
			}
		} else {
			RenderSystem.bindDefaultUniforms(pass);
		}
		return pass;
	}

	private static FeatureRenderDispatcher dispatcher() {
		if (dispatcher == null) {
			Minecraft mc = Minecraft.getInstance();
			buffers = new RenderBuffers(1);
			bufferSource = new PhasedBufferSource();
			// The dispatcher renders the storage it was created with. FeatureRenderDispatcherAccessor swaps it.
			dispatcher = new FeatureRenderDispatcher(new SubmitNodeStorage(), mc.getBlockRenderer(), bufferSource, mc.getAtlasManager(),
				buffers.outlineBufferSource(), buffers.crumblingBufferSource(), mc.font);
		}
		return dispatcher;
	}

	static void close() {
		if (dispatcher != null) {
			dispatcher.close();
			dispatcher = null;
		}
		buffers = null;
		if (bufferSource != null) {
			bufferSource.close();
			bufferSource = null;
		}
	}

	/**
	 * A buffer source that takes only the solid or only the translucent render types and drops the others. It takes
	 * all of them while {@link #translucent} is null.
	 */
	private static final class PhasedBufferSource extends MultiBufferSource.BufferSource {
		@Nullable Boolean translucent;
		private final ByteBufferBuilder buffer;

		PhasedBufferSource() {
			this(new ByteBufferBuilder(786432));
		}

		private PhasedBufferSource(ByteBufferBuilder buffer) {
			super(buffer, new LinkedHashMap<>());
			this.buffer = buffer;
		}

		void close() {
			this.buffer.close();
		}

		@Override
		public VertexConsumer getBuffer(RenderType type) {
			if (this.translucent != null && type.pipeline().getBlendFunction().isPresent() != this.translucent) {
				return Discard.INSTANCE;
			}
			return super.getBuffer(type);
		}
	}

	private enum Discard implements VertexConsumer {
		INSTANCE;

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer setColor(int argb) {
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}
	}
}
