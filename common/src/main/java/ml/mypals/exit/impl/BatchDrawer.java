package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders batches into a target. A {@link Batch} is a buffer source that keeps one buffer per render type, so nothing
 * is drawn before the batch is. It sets this frame's camera rotation and projection itself, because the level renderer
 * may have reset them by the time EXIT renders. Each render type opens its own pass on the main target when it is
 * drawn, so those passes are pointed at the target meanwhile. The solid render types are drawn first, then the translucent
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
		// The batch holds vanilla vertices. Before 1.21.5 Iris widens no render type's format, but with isRenderingLevel on
		// it treats what is drawn as level geometry (its buffer source and entity render context), so keep it off.
		boolean renderingLevel = IrisCompat.isRenderingLevel();
		IrisCompat.setRenderingLevel(false);
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.set(ExitRuntime.viewRotation());
		try {
			executeInto(target, () -> {
				batch.colour.draw(false);
				batch.colour.draw(true);
			});
			// Inside a portal the main target is the channel's, see ExitRuntime.targetFor.
			Channel channel = ExitRuntime.routingChannel();
			if (channel != null && target == channel.drawnTarget()) {
				TranslucentDepth.record(() -> batch.depth.draw(true), channel.translucentDepth());
			}
		} finally {
			modelView.popMatrix();
			IrisCompat.setRenderingLevel(renderingLevel);
			batch.discard();
		}
	}

	/**
	 * Whether the render type is translucent, so it is drawn after the solid ones. Before 1.21.5 a render type keeps its
	 * blending in a state shard of a type EXIT cannot name, so it goes by whether it sorts its quads, as the translucent
	 * ones do.
	 */
	static boolean translucent(RenderType type) {
		return type.sortOnUpload();
	}

	/**
	 * Runs {@code execute} with {@code target} bound and handed out as the main target (see {@link ExitRuntime#setOutput}),
	 * since render types bind the main target themselves, and with the level projection set, since they read it from
	 * RenderSystem when they draw. Drawings into a channel blend their alpha as the merge expects (GlOverrides).
	 */
	static void executeInto(RenderTarget target, Runnable execute) {
		RenderTarget saved = ExitRuntime.output();
		Matrix4f projection = ExitRuntime.levelProjection();
		boolean intoChannel = target != Gpu.mainTarget();
		ExitRuntime.setOutput(target);
		Runnable restoreBinding = Gpu.bindForDrawing(target);
		if (intoChannel) {
			GlOverrides.intoChannel++;
		}
		if (projection != null) {
			RenderSystem.backupProjectionMatrix();
			RenderSystem.setProjectionMatrix(new Matrix4f(projection), RenderSystem.getProjectionType());
		}
		try {
			execute.run();
		} finally {
			if (intoChannel) {
				GlOverrides.intoChannel--;
			}
			restoreBinding.run();
			ExitRuntime.setOutput(saved);
			if (projection != null) {
				RenderSystem.restoreProjectionMatrix();
			}
		}
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
			// The batch holds vanilla vertices. Iris 1.8 widens the format of every buffer started while a pack is in use
			// unless skipExtension is set. (On 1.21.5 the render type's format is also widened while isRenderingLevel is on;
			// Iris 1.8.1 and 1.8.8 only use it for normals and their entity render context.)
			boolean skipExtension = IrisCompat.skipExtension();
			boolean renderingLevel = IrisCompat.isRenderingLevel();
			IrisCompat.setSkipExtension(true);
			IrisCompat.setRenderingLevel(false);
			try {
				builder = new BufferBuilder(bytes, type.mode(), type.format());
			} finally {
				IrisCompat.setSkipExtension(skipExtension);
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
