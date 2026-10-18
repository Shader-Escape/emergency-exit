package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import ml.mypals.exit.compat.IrisCompat;
import ml.mypals.exit.mixin.CompositeRenderTypeAccessor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4fStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Renders batches into a target. A {@link Batch} is a buffer source that keeps one buffer per render type, so nothing
 * is drawn before the batch is. It sets this frame's camera rotation and projection itself, because the level renderer
 * may have reset them by the time EXIT renders. Each render type opens its own pass on the output target when it is
 * drawn, so the output is pointed at the target meanwhile. The solid render types are drawn first, then the translucent
 * ones. The batch keeps a second copy of the translucent vertices, which the translucent depth draws.
 */
public final class BatchDrawer {
	/** A buffer source that throws everything away. */
	static final MultiBufferSource DISCARD = type -> Discard.INSTANCE;

	private BatchDrawer() {}

	/** Renders {@code batch} into {@code target} and empties it. */
	static void draw(Batch batch, RenderTarget target) {
		if (batch.isEmpty()) {
			return;
		}
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.set(ExitRuntime.viewRotation());
		try {
			long opened = ExitRuntime.openedPasses();
			executeInto(Objects.requireNonNull(target.getColorTextureView(), "target without colour"), target.getDepthTextureView(), () -> {
				batch.colour.draw(false);
				batch.colour.draw(true);
			});
			// Inside a portal a pass on the main target was moved into the channel's target.
			Channel channel = ExitRuntime.routingChannel();
			if (ExitRuntime.openedPasses() != opened && channel != null && (target == Gpu.mainTarget() || target == channel.drawnTarget())) {
				TranslucentDepth.record(() -> batch.depth.draw(true), channel.prepareForDraw(), channel.translucentDepth());
			}
		} finally {
			modelView.popMatrix();
			batch.discard();
		}
	}

	/** Whether the render type blends, so it is drawn after the solid ones. */
	static boolean translucent(RenderType type) {
		if (type instanceof CompositeRenderTypeAccessor composite) {
			return composite.emergency_exit$renderPipeline().getBlendFunction().isPresent();
		}
		// Render types of other mods, and the ones Iris wraps: the translucent ones sort their quads.
		return type.sortOnUpload();
	}

	/**
	 * Runs {@code execute} with the output of the render types pointed at the given colour and depth, and with the level
	 * projection set, since each render type binds the default uniforms itself.
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

	/**
	 * The buffer source of a batch. Unlike vanilla's buffer sources it never draws while it is being filled. The
	 * vertices of translucent render types also go into a second set of buffers, for the translucent depth.
	 */
	static final class Batch implements MultiBufferSource {
		private final TypeBuffers colour = new TypeBuffers();
		private final TypeBuffers depth = new TypeBuffers();

		@Override
		public VertexConsumer getBuffer(RenderType type) {
			VertexConsumer buffer = this.colour.getBuffer(type);
			return translucent(type) ? VertexMultiConsumer.create(buffer, this.depth.getBuffer(type)) : buffer;
		}

		boolean isEmpty() {
			return this.colour.isEmpty();
		}

		/** Throws away what was drawn into the batch and keeps its memory. */
		void discard() {
			this.colour.discard();
			this.depth.discard();
		}

		void close() {
			this.colour.close();
			this.depth.close();
		}
	}

	/** One buffer per render type, kept from frame to frame. */
	private static final class TypeBuffers {
		private final Map<RenderType, ByteBufferBuilder> memory = new LinkedHashMap<>();
		private final Map<RenderType, BufferBuilder> started = new HashMap<>();
		/**
		 * Finished meshes of render types whose primitives connect (strips, fans). Vanilla draws such a buffer when it
		 * is asked for again, so that two drawings are not joined. Here it is finished and kept for later.
		 */
		private final Map<RenderType, List<MeshData>> finished = new HashMap<>();

		VertexConsumer getBuffer(RenderType type) {
			BufferBuilder builder = this.started.get(type);
			if (builder != null) {
				if (type.canConsolidateConsecutiveGeometry()) {
					return builder;
				}
				MeshData mesh = builder.build();
				if (mesh != null) {
					this.finished.computeIfAbsent(type, t -> new ArrayList<>()).add(mesh);
				}
			}
			ByteBufferBuilder bytes = this.memory.computeIfAbsent(type, t -> new ByteBufferBuilder(t.bufferSize()));
			// Iris widens the format of a buffer started while the level renders, but the vanilla pipelines expect the
			// vanilla format.
			boolean renderingLevel = IrisCompat.isRenderingLevel();
			IrisCompat.setRenderingLevel(false);
			try {
				builder = new BufferBuilder(bytes, type.mode(), type.format());
			} finally {
				IrisCompat.setRenderingLevel(renderingLevel);
			}
			this.started.put(type, builder);
			return builder;
		}

		boolean isEmpty() {
			return this.started.isEmpty() && this.finished.isEmpty();
		}

		/** Draws the solid or the translucent render types, in the order they were first used. */
		void draw(boolean translucent) {
			for (RenderType type : List.copyOf(this.memory.keySet())) {
				if (translucent(type) == translucent) {
					this.draw(type);
				}
			}
		}

		private void draw(RenderType type) {
			List<MeshData> meshes = this.finished.remove(type);
			if (meshes != null) {
				for (MeshData mesh : meshes) {
					this.draw(type, mesh);
				}
			}
			BufferBuilder builder = this.started.remove(type);
			MeshData mesh = builder != null ? builder.build() : null;
			if (mesh != null) {
				this.draw(type, mesh);
			}
		}

		private void draw(RenderType type, MeshData mesh) {
			if (type.sortOnUpload()) {
				mesh.sortQuads(this.memory.get(type), RenderSystem.getProjectionType().vertexSorting());
			}
			type.draw(mesh);
		}

		void discard() {
			for (List<MeshData> meshes : this.finished.values()) {
				meshes.forEach(MeshData::close);
			}
			this.finished.clear();
			for (BufferBuilder builder : this.started.values()) {
				MeshData mesh = builder.build();
				if (mesh != null) {
					mesh.close();
				}
			}
			this.started.clear();
		}

		void close() {
			this.discard();
			this.memory.values().forEach(ByteBufferBuilder::close);
			this.memory.clear();
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
	}
}
