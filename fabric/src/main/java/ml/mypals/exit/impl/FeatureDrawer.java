package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A channel's batch: a buffer source the caller records into, rendered into the channel's target at the end of the
 * level rendering. 1.17 has no {@code SubmitNodeCollector}, so the submits are collected as vanilla render types and
 * drawn here with plain OpenGL. The solid render types are drawn first, then the translucent ones; a translucent
 * submit is recorded into a second buffer as well, which is drawn colour off and depth on into the channel's
 * translucent-depth target, so the channel can be blended by depth.
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

		private final Map<RenderType, Buffers> buffers = new LinkedHashMap<>();
		private boolean closed;

		@Override
		public VertexConsumer getBuffer(RenderType type) {
			Buffers entry = this.buffers.get(type);
			if (entry == null) {
				entry = new Buffers(type, isTranslucent(type));
				this.buffers.put(type, entry);
			}
			return entry.consumer();
		}

		void draw(RenderTarget target, @Nullable RenderTarget translucentDepth) {
			if (this.closed) {
				return;
			}
			this.closed = true;
			if (this.buffers.isEmpty()) {
				return;
			}
			Map<RenderType, BufferBuilder> solid = new LinkedHashMap<>();
			Map<RenderType, BufferBuilder> translucent = new LinkedHashMap<>();
			Map<RenderType, BufferBuilder> translucentDepthData = new LinkedHashMap<>();
			for (Map.Entry<RenderType, Buffers> entry : this.buffers.entrySet()) {
				(entry.getValue().translucent ? translucent : solid).put(entry.getKey(), entry.getValue().colour);
				if (entry.getValue().depth != null) {
					translucentDepthData.put(entry.getKey(), entry.getValue().depth);
				}
			}

			// 1.16.5 transforms with the fixed-function pipeline, so the GL matrix stacks are where the level's view
			// rotation and projection live; the pack's passes leave them wherever they were.
			RenderSystem.matrixMode(GL11.GL_PROJECTION);
			RenderSystem.pushMatrix();
			RenderSystem.loadIdentity();
			RenderSystem.multMatrix(ExitRuntime.projectionMatrix());
			RenderSystem.matrixMode(GL11.GL_MODELVIEW);
			RenderSystem.pushMatrix();
			RenderSystem.loadIdentity();
			RenderSystem.multMatrix(ExitRuntime.viewRotation());
			// Iris widens the vertex formats while the level renders; the buffers are already in the vanilla format.
			boolean renderingLevel = IrisCompat.isRenderingLevel();
			IrisCompat.setRenderingLevel(false);
			Runnable restore = Gpu.bindForDrawing(target);
			try {
				// Solid render types first, then the translucent ones, so a solid submit never covers a nearer translucent.
				for (Map.Entry<RenderType, BufferBuilder> entry : solid.entrySet()) {
					entry.getKey().end(entry.getValue(), 0, 0, 0);
				}
				for (Map.Entry<RenderType, BufferBuilder> entry : translucent.entrySet()) {
					entry.getKey().end(entry.getValue(), 0, 0, 0);
				}
				if (translucentDepth != null) {
					Runnable restoreDepth = Gpu.bindForDrawing(translucentDepth);
					try {
						for (Map.Entry<RenderType, BufferBuilder> entry : translucentDepthData.entrySet()) {
							drawDepthOnly(entry.getKey(), entry.getValue());
						}
					} finally {
						restoreDepth.run();
					}
				}
			} finally {
				restore.run();
				IrisCompat.setRenderingLevel(renderingLevel);
				RenderSystem.matrixMode(GL11.GL_MODELVIEW);
				RenderSystem.popMatrix();
				RenderSystem.matrixMode(GL11.GL_PROJECTION);
				RenderSystem.popMatrix();
				RenderSystem.matrixMode(GL11.GL_MODELVIEW);
				this.buffers.clear();
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

		/**
		 * Draws one recorded buffer with nothing written to colour. 1.17's {@code RenderType.end(builder, x, y, z)}
		 * does this for a normal draw (it is what {@code MultiBufferSource.BufferSource} uses); here the buffer is
		 * ended and uploaded with the colour mask off instead.
		 */
		private static void drawDepthOnly(RenderType type, BufferBuilder buffer) {
			if (!buffer.building()) {
				return;
			}
			buffer.end();
			type.setupRenderState();
			try {
				GL11.glColorMask(false, false, false, false);
				GL11.glDepthMask(true);
				BufferUploader.end(buffer);
			} finally {
				GL11.glColorMask(true, true, true, true);
				type.clearRenderState();
			}
		}

		/** The builders of one render type. Translucent types also get a depth-only copy. */
		private static final class Buffers {
			final BufferBuilder colour;
			@Nullable final BufferBuilder depth;
			final boolean translucent;
			private final VertexConsumer consumer;

			Buffers(RenderType type, boolean translucent) {
				this.translucent = translucent;
				this.colour = begin(type);
				this.depth = translucent ? begin(type) : null;
				this.consumer = this.depth == null ? this.colour : new DualConsumer(this.colour, this.depth);
			}

			VertexConsumer consumer() {
				return this.consumer;
			}

			private static BufferBuilder begin(RenderType type) {
				BufferBuilder builder = new BufferBuilder(INITIAL_BUFFER_SIZE);
				builder.begin(type.mode(), type.format());
				return builder;
			}
		}
	}

	/** Writes every vertex into two builders. */
	private record DualConsumer(BufferBuilder first, BufferBuilder second) implements VertexConsumer {
		@Override
		public VertexConsumer vertex(double x, double y, double z) {
			this.first.vertex(x, y, z);
			this.second.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			this.first.color(red, green, blue, alpha);
			this.second.color(red, green, blue, alpha);
			return this;
		}

		@Override
		public VertexConsumer uv(float u, float v) {
			this.first.uv(u, v);
			this.second.uv(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlayCoords(int u, int v) {
			this.first.overlayCoords(u, v);
			this.second.overlayCoords(u, v);
			return this;
		}

		@Override
		public VertexConsumer uv2(int u, int v) {
			this.first.uv2(u, v);
			this.second.uv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			this.first.normal(x, y, z);
			this.second.normal(x, y, z);
			return this;
		}

		@Override
		public void endVertex() {
			this.first.endVertex();
			this.second.endVertex();
		}
	}
}
