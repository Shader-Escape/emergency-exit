package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.Minecraft;
import ml.mypals.exit.mixin.FeatureRenderDispatcherAccessor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.joml.Matrix4fStack;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Renders submit storages into a target with our own feature dispatcher. It sets this frame's camera rotation and
 * projection itself, because the level renderer may have reset them by the time EXIT renders. Before 26.2 the features
 * are drawn into a buffer source, and each batch opens its own pass on the output target when it ends, so the output
 * is pointed at the target while the features are drawn. In 1.21.11 the dispatcher draws all features at once and then
 * empties the storage; {@link #keepSubmits} keeps them for the second drawing of the translucent depth.
 */
public final class FeatureDrawer {
	private static RenderBuffers buffers;
	private static FeatureRenderDispatcher dispatcher;
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
		MultiBufferSource.BufferSource bufferSource = buffers.bufferSource();
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
				renderAll(dispatcher, bufferSource);
				buffers.outlineBufferSource().endOutlineBatch();
				if (direct != null) {
					direct.accept(bufferSource);
					bufferSource.endBatch();
				}
			});
			// Inside a portal a pass on the main target was moved into the channel's target.
			Channel channel = ExitRuntime.routingChannel();
			if (ExitRuntime.openedPasses() != opened && channel != null && (target == Gpu.mainTarget() || target == channel.drawnTarget())) {
				// All features are drawn again. The solid ones only write the depth the channel already has.
				TranslucentDepth.record(() -> renderAll(dispatcher, bufferSource), channel.prepareForDraw(), channel.translucentDepth());
			}
		} finally {
			modelView.popMatrix();
			IrisCompat.setRenderingLevel(renderingLevel);
			storage.clear();
		}
	}

	private static void renderAll(FeatureRenderDispatcher dispatcher, MultiBufferSource.BufferSource bufferSource) {
		keepSubmits = true;
		try {
			dispatcher.renderAllFeatures();
		} finally {
			keepSubmits = false;
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
			// The dispatcher renders the storage it was created with. FeatureRenderDispatcherAccessor swaps it.
			dispatcher = new FeatureRenderDispatcher(new SubmitNodeStorage(), mc.getBlockRenderer(), buffers.bufferSource(), mc.getAtlasManager(),
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
	}
}
