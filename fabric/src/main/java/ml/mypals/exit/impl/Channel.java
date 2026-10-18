package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import ml.mypals.exit.api.ExitChannel;
import ml.mypals.exit.api.ExitContext;
import ml.mypals.exit.api.ExitOptions;
import ml.mypals.exit.api.PortalOverrides;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A channel with its own colour and depth target. With a shader pack, its drawings go here. The depth is seeded from
 * the scene, so the world still hides the drawings. At the end of the level rendering the target is merged back.
 */
final class Channel implements ExitChannel {
	private final ResourceLocation id;
	private final ExitOptions options;
	final int order;

	private @Nullable RenderTarget target;
	/** The depth of the translucent drawings, see {@link TranslucentDepth}. */
	private @Nullable RenderTarget translucentDepth;
	private boolean translucentDrawn;
	private boolean preparedThisFrame;
	private boolean usedThisFrame;
	private boolean usedLastFrame;
	private @Nullable FeatureDrawer.Batch batch;

	Channel(ResourceLocation id, ExitOptions options, int order) {
		this.id = id;
		this.options = options;
		this.order = order;
	}

	@Override
	public ResourceLocation id() {
		return this.id;
	}

	@Override
	public ExitOptions options() {
		return this.options;
	}

	@Override
	public void draw(@Nullable MultiBufferSource passThrough, Consumer<ExitContext> drawing) {
		RenderSystem.assertThread(RenderSystem::isOnRenderThread);
		DrawContext outer = ExitRuntime.drawing;
		if (outer != null) {
			if (outer.channel != this) {
				throw new IllegalStateException("EXIT: a drawing on " + this.id + " inside one on " + outer.channel.id);
			}
			drawing.accept(outer);
			return;
		}
		if (!ExitRuntime.inLevel()) {
			ExitRuntime.warnOnce("draw-outside-level", "EXIT: " + this.id + " drew outside the level rendering; the drawing was dropped");
			return;
		}
		if (ExitRuntime.inShadowPass()) {
			// EXIT draws nothing into the shader pack's shadow map.
			return;
		}

		boolean routed = ExitRuntime.begin(this, null);
		try {
			// Recorded into the batch, so the whole channel is drawn in one frame: solid before translucent.
			MultiBufferSource source = !routed && passThrough != null ? passThrough : this.batch();
			this.run(new DrawContext(this, source, routed, ExitRuntime.camera()), drawing);
		} finally {
			ExitRuntime.end();
		}
	}

	private void run(DrawContext context, Consumer<ExitContext> drawing) {
		ExitRuntime.drawing = context;
		try {
			drawing.accept(context);
		} finally {
			ExitRuntime.drawing = null;
		}
	}

	@Override
	public void portal(PortalOverrides overrides, Runnable body) {
		if (!RenderSystem.isOnRenderThread()) {
			ExitRuntime.portalOffThread(overrides, body);
			return;
		}
		if (ExitRuntime.inShadowPass()) {
			return;
		}
		ExitRuntime.begin(this, overrides);
		try {
			body.run();
		} finally {
			ExitRuntime.end();
		}
	}

	@Override
	public RenderTarget redirect(RenderTarget original) {
		return ExitRuntime.redirect(this, original);
	}

	@Override
	public MultiBufferSource batch(@Nullable MultiBufferSource passThrough) {
		RenderSystem.assertThread(RenderSystem::isOnRenderThread);
		if (!ExitRuntime.inLevel()) {
			ExitRuntime.warnOnce("batch-outside-level", "EXIT: " + this.id + " asked for its batch outside the level rendering; it is dropped");
			return passThrough != null ? passThrough : ExitRuntime.discard();
		}
		if (ExitRuntime.inShadowPass()) {
			return ExitRuntime.discard();
		}
		if (!ExitRuntime.isActive() && passThrough != null) {
			return passThrough;
		}
		return this.batchStorage();
	}

	private FeatureDrawer.Batch batchStorage() {
		if (this.batch == null) {
			this.batch = FeatureDrawer.newBatch();
		}
		return this.batch;
	}

	/** Whether the channel will draw this frame. Asked before the scene depth is copied. */
	boolean wantsFrame() {
		if (this.options.wantsFrame() != null) {
			return this.options.wantsFrame().getAsBoolean();
		}
		return this.usedLastFrame || this.usedThisFrame || this.batch != null;
	}

	@Override
	public void flushBatch() {
		RenderSystem.assertThread(RenderSystem::isOnRenderThread);
		if (ExitRuntime.passesRunning() && !ExitRuntime.inShadowPass()) {
			this.drawBatch();
		}
	}

	/** Renders this frame's batch into the channel's target, or into the main target when not routed. */
	void drawBatch() {
		FeatureDrawer.Batch submitted = this.batch;
		this.batch = null;
		if (submitted == null) {
			return;
		}
		boolean routed = ExitRuntime.begin(this, null);
		try {
			FeatureDrawer.draw(submitted, routed ? this.prepareForDraw() : Gpu.mainTarget(), routed ? this.translucentDepth() : null);
		} finally {
			ExitRuntime.end();
		}
	}

	/** The channel's target. It is cleared and seeded with the scene depth on its first use in a frame. */
	RenderTarget prepareForDraw() {
		RenderTarget output = this.ensureTarget();
		if (!this.preparedThisFrame) {
			Gpu.clear(output);
			// Iris 1.2 and older keep the pack's scene depth in a texture of their own; seeding reads the main depth.
			SceneDepth.syncMainDepth();
			DepthSeed.seed(Gpu.mainTarget(), output, this.options);
			this.preparedThisFrame = true;
			this.translucentDrawn = false;
		}
		this.usedThisFrame = true;
		return output;
	}

	/**
	 * The target for the depth of the translucent drawings. On its first use in a frame it starts from the seeded
	 * depth, so the world hides them there too. Call it after {@link #prepareForDraw}.
	 */
	RenderTarget translucentDepth() {
		RenderTarget output = Objects.requireNonNull(this.target);
		if (this.translucentDepth == null) {
			this.translucentDepth = Gpu.depthTarget(this.label() + "_translucent_depth", output.width, output.height);
		} else if (this.translucentDepth.width != output.width || this.translucentDepth.height != output.height) {
			Gpu.resize(this.translucentDepth, output.width, output.height);
		}
		if (!this.translucentDrawn) {
			Gpu.copyDepth(this.translucentDepth, output);
			this.translucentDrawn = true;
		}
		return this.translucentDepth;
	}

	/** Makes the channel's depth the nearer of its own and the translucent drawings'. Called before merging. */
	void foldTranslucentDepth() {
		if (this.translucentDrawn && this.usedThisFrame && this.target != null && this.translucentDepth != null) {
			TranslucentDepth.fold(this.target, this.translucentDepth);
		}
		this.translucentDrawn = false;
	}

	private String label() {
		return "emergency_exit_" + this.id.getNamespace() + "_" + this.id.getPath().replace('/', '_');
	}

	private RenderTarget ensureTarget() {
		int width = Gpu.sceneWidth();
		int height = Gpu.sceneHeight();
		if (this.target == null) {
			this.target = Gpu.colorDepthTarget(this.label(), width, height);
		} else if (this.target.width != width || this.target.height != height) {
			Gpu.resize(this.target, width, height);
		}
		return this.target;
	}

	/** Merges the target onto the main one if something was drawn into it this frame. */
	boolean composite(RenderTarget main) {
		if (this.target == null || !this.usedThisFrame) {
			return false;
		}
		Composite.draw(main, this.target, this.options);
		return true;
	}

	/** The channel's target if something was drawn into it this frame, otherwise null. */
	@Nullable RenderTarget drawnTarget() {
		return this.usedThisFrame ? this.target : null;
	}

	/** Replaces the frame with a debug view of this channel. */
	void showDebugView(RenderTarget main) {
		RenderTarget output = this.ensureTarget();
		if (!this.usedThisFrame) {
			Gpu.clear(output);
		}
		DebugView.draw(main, output);
	}

	void endFrame() {
		this.usedLastFrame = this.usedThisFrame;
		this.preparedThisFrame = false;
		this.usedThisFrame = false;
		this.translucentDrawn = false;
		this.batch = null;
	}

	void close() {
		if (this.target != null) {
			this.target.destroyBuffers();
			this.target = null;
		}
		if (this.translucentDepth != null) {
			this.translucentDepth.destroyBuffers();
			this.translucentDepth = null;
		}
		this.translucentDrawn = false;
		this.preparedThisFrame = false;
		this.usedThisFrame = false;
		this.batch = null;
	}
}
