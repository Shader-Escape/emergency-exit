package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
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
 * projection itself, because the level renderer may have reset them by the time EXIT renders.
 */
final class FeatureDrawer {
	private static RenderBuffers buffers;
	private static FeatureRenderDispatcher dispatcher;

	private FeatureDrawer() {}

	static SubmitNodeStorage newStorage() {
		SubmitNodeStorage storage = new SubmitNodeStorage();
		storage.setUseImprovedTransparency(Minecraft.getInstance().gameRenderer.useImprovedTransparency());
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
			if (!frame.isEmpty()) {
				try (RenderPass pass = openPass("emergency_exit_features", target)) {
					FeatureRenderDispatcher.renderAllFeatures(pass, frame);
				}
				// Inside a portal a pass on the main target was moved into the channel's target.
				Channel channel = ExitRuntime.routingChannel();
				if (channel != null && (target == Gpu.mainTarget() || target == channel.drawnTarget())) {
					TranslucentDepth.record(frame, channel.prepareForDraw(), channel.translucentDepth());
				}
			}
		} finally {
			buffers.endFrame();
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
