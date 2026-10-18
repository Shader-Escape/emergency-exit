package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A channel's batch: a buffer source the caller records into, rendered into the channel's target at the end of the
 * level rendering. 1.20.6 has no {@code SubmitNodeCollector}, so the submits are collected as vanilla render types and
 * drawn here with plain OpenGL. The solid render types are drawn first, then the translucent ones; the translucent
 * ones are drawn a second time, colour off and depth on, into the channel's translucent-depth target.
 */
public final class FeatureDrawer {
	private FeatureDrawer() {}

	/** A new batch to record into this frame. Also used where a drawing must be dropped: it is simply never drawn. */
	static Batch newBatch() {
		return new Batch();
	}

	/** Renders {@code batch} into {@code target}; the translucent submits also write {@code translucentDepth}. */
	static void draw(MultiBufferSource batch, RenderTarget target, @Nullable RenderTarget translucentDepth) {
		if (batch instanceof Batch recording) {
			recording.draw(target, translucentDepth);
		}
	}

	static void close() {
	}

	/** Collects submits per render type and draws them in one go. */
	static final class Batch implements MultiBufferSource {
		private static final int INITIAL_BUFFER_SIZE = 786432;

		private final Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
		private boolean closed;

		@Override
		public VertexConsumer getBuffer(RenderType type) {
			return this.builders.computeIfAbsent(type, t -> new BufferBuilder(INITIAL_BUFFER_SIZE));
		}

		void draw(RenderTarget target, @Nullable RenderTarget translucentDepth) {
			if (this.closed) {
				return;
			}
			this.closed = true;
			if (this.builders.isEmpty()) {
				this.close();
				return;
			}
			Map<RenderType, BufferBuilder.RenderedBuffer> rendered = new LinkedHashMap<>();
			for (Map.Entry<RenderType, BufferBuilder> entry : this.builders.entrySet()) {
				BufferBuilder.RenderedBuffer buffer = entry.getValue().endOrDiscardIfEmpty();
				if (buffer != null) {
					rendered.put(entry.getKey(), buffer);
				}
			}

			Matrix4fStack modelView = RenderSystem.getModelViewStack();
			modelView.pushMatrix();
			modelView.set(ExitRuntime.viewRotation());
			RenderSystem.applyModelViewMatrix();
			Matrix4f projection = RenderSystem.getProjectionMatrix();
			RenderSystem.setProjectionMatrix(new Matrix4f(ExitRuntime.projectionMatrix()), VertexSorting.DISTANCE_TO_ORIGIN);
			// Iris widens the vertex formats while the level renders; the buffers are already in the vanilla format.
			boolean renderingLevel = IrisCompat.isRenderingLevel();
			IrisCompat.setRenderingLevel(false);
			Runnable restore = Gpu.bindForDrawing(target);
			try {
				// Solid render types first, then the translucent ones, so a solid submit never covers a nearer translucent.
				for (Map.Entry<RenderType, BufferBuilder.RenderedBuffer> entry : rendered.entrySet()) {
					boolean translucent = isTranslucent(entry.getKey());
					if (!translucent) {
						draw(entry.getKey(), entry.getValue());
					}
				}
				for (Map.Entry<RenderType, BufferBuilder.RenderedBuffer> entry : rendered.entrySet()) {
					if (!isTranslucent(entry.getKey())) {
						continue;
					}
					draw(entry.getKey(), entry.getValue());
					if (translucentDepth != null) {
						// The translucent submits write no depth; draw them again, colour off and depth on, into the
						// channel's translucent-depth target, so the channel can be blended by depth.
						Runnable restoreDepth = Gpu.bindForDrawing(translucentDepth);
						try {
							GL11.glColorMask(false, false, false, false);
							GL11.glDepthMask(true);
							draw(entry.getKey(), entry.getValue());
						} finally {
							GL11.glColorMask(true, true, true, true);
							restoreDepth.run();
						}
					}
				}
			} finally {
				restore.run();
				IrisCompat.setRenderingLevel(renderingLevel);
				RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
				modelView.popMatrix();
				RenderSystem.applyModelViewMatrix();
				for (BufferBuilder.RenderedBuffer buffer : rendered.values()) {
					buffer.release();
				}
				this.close();
			}
		}

		/** Whether the render type blends, found from the state it sets up (there is no pipeline to ask here). */
		private static boolean isTranslucent(RenderType type) {
			type.setupRenderState();
			try {
				return GL11.glIsEnabled(GL11.GL_BLEND);
			} finally {
				type.clearRenderState();
			}
		}

		/** Draws one render type's recorded buffer. */
		private static void draw(RenderType type, BufferBuilder.RenderedBuffer buffer) {
			type.setupRenderState();
			try {
				BufferUploader.drawWithShader(buffer);
			} finally {
				type.clearRenderState();
			}
		}

		private void close() {
			this.builders.clear();
		}
	}
}
