package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.joml.Matrix4fStack;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Renders submit storages into a target with our own feature dispatcher. It sets this frame's camera rotation and
 * projection itself, because the level renderer may have reset them by the time EXIT renders. Each feature opens its
 * own pass on the output target, so the output is pointed at the target while the features are drawn.
 */
final class FeatureDrawer {
	private static RenderBuffers buffers;
	private static FeatureRenderDispatcher dispatcher;

	private FeatureDrawer() {}

	static SubmitNodeStorage newStorage() {
		SubmitNodeStorage storage = new SubmitNodeStorage();
		return storage;
	}

	/** Renders {@code storage} into {@code target} and empties it. */
	static void draw(SubmitNodeStorage storage, RenderTarget target) {
		// The vertices are written here. Iris would widen their format while the level renders, but the vanilla
		// pipelines expect the vanilla format.
		boolean renderingLevel = IrisCompat.isRenderingLevel();
		IrisCompat.setRenderingLevel(false);
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.set(ExitRuntime.viewRotation());
		FeatureRenderDispatcher.PreparedFrame frame;
		try {
			frame = dispatcher().prepareFrame(storage);
		} finally {
			modelView.popMatrix();
			IrisCompat.setRenderingLevel(renderingLevel);
		}

		try (frame) {
			long opened = ExitRuntime.openedPasses();
			executeInto(Objects.requireNonNull(target.getColorTextureView(), "target without colour"), target.getDepthTextureView(), () -> {
				frame.executeSolid();
				frame.executeTranslucent();
				frame.executeTranslucentAfterTerrain();
				frame.executeAlwaysOnTop();
			});
			// Inside a portal a pass on the main target was moved into the channel's target.
			Channel channel = ExitRuntime.routingChannel();
			if (ExitRuntime.openedPasses() != opened && channel != null && (target == Gpu.mainTarget() || target == channel.drawnTarget())) {
				TranslucentDepth.record(frame, channel.prepareForDraw(), channel.translucentDepth());
			}
		} finally {
			buffers.endFrame();
		}
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
			() -> label, color, Optional.empty(), depth, OptionalDouble.empty());
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
			dispatcher = new FeatureRenderDispatcher(buffers, mc.getModelManager(), mc.getAtlasManager(), mc.font, mc.gameRenderer.gameRenderState());
		}
		return dispatcher;
	}

	static void close() {
		if (dispatcher != null) {
			dispatcher.close();
			dispatcher = null;
		}
		if (buffers != null) {
			buffers.close();
			buffers = null;
		}
	}
}
