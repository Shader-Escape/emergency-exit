package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import ml.mypals.exit.api.ExitChannel;
import ml.mypals.exit.api.ExitContext;
import ml.mypals.exit.api.ExitOptions;
import ml.mypals.exit.api.PortalOverrides;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.util.Util;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * A channel with its own colour and depth target. With a shader pack, its drawings go here. The depth is seeded from
 * the scene, so the world still hides the drawings. At the end of the level rendering the target is merged back.
 */
final class Channel implements ExitChannel {
	private final Identifier id;
	private final ExitOptions options;
	final int order;

	private @Nullable TextureTarget target;
	private boolean preparedThisFrame;
	private boolean usedThisFrame;
	private boolean usedLastFrame;
	private @Nullable SubmitNodeStorage batch;
	private final SimpleGizmoCollector gizmos = new SimpleGizmoCollector();

	Channel(Identifier id, ExitOptions options, int order) {
		this.id = id;
		this.options = options;
		this.order = order;
	}

	@Override
	public Identifier id() {
		return this.id;
	}

	@Override
	public ExitOptions options() {
		return this.options;
	}

	@Override
	public void draw(@Nullable SubmitNodeCollector passThrough, Consumer<ExitContext> drawing) {
		RenderSystem.assertOnRenderThread();
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
			if (!routed && passThrough != null) {
				this.run(new DrawContext(this, passThrough, Gpu.mainTarget(), false, ExitRuntime.camera()), drawing);
				return;
			}
			if (Gpu.isInRenderPass() || !ExitRuntime.passesRunning()) {
				// No pass can be opened inside another one, and the scene depth is not ready yet. Collect now and render
				// with the batch later.
				this.run(new DrawContext(this, this.batchStorage(), null, routed, ExitRuntime.camera()), drawing);
				return;
			}
			RenderTarget output = routed ? this.prepareForDraw() : Gpu.mainTarget();
			SubmitNodeStorage storage = FeatureDrawer.newStorage();
			this.run(new DrawContext(this, storage, output, routed, ExitRuntime.camera()), drawing);
			FeatureDrawer.draw(storage, output);
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
		boolean routed = ExitRuntime.begin(this, overrides);
		try {
			if (routed) {
				try (Gizmos.TemporaryCollection ignored = Gizmos.withCollector(this.gizmos)) {
					body.run();
				}
			} else {
				body.run();
			}
		} finally {
			ExitRuntime.end();
		}
	}

	@Override
	public RenderTarget redirect(RenderTarget original) {
		return ExitRuntime.redirect(this, original);
	}

	@Override
	public SubmitNodeCollector batch(@Nullable SubmitNodeCollector passThrough) {
		RenderSystem.assertOnRenderThread();
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

	private SubmitNodeStorage batchStorage() {
		if (this.batch == null) {
			this.batch = FeatureDrawer.newStorage();
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

	/** Submits the gizmos collected in portals to the batch. Gizmos that persist stay for later frames. */
	int submitGizmos() {
		List<SimpleGizmoCollector.GizmoInstance> collected = this.gizmos.drainGizmos();
		if (collected.isEmpty()) {
			return 0;
		}
		long now = Util.getMillis();
		DrawableGizmoPrimitives standard = new DrawableGizmoPrimitives();
		DrawableGizmoPrimitives onTop = new DrawableGizmoPrimitives();
		for (SimpleGizmoCollector.GizmoInstance gizmo : collected) {
			gizmo.gizmo().emit(gizmo.isAlwaysOnTop() ? onTop : standard, gizmo.getAlphaMultiplier(now));
		}
		SubmitNodeStorage storage = this.batchStorage();
        assert ExitRuntime.camera() != null;
        standard.submit(storage, ExitRuntime.camera(), false);
		onTop.submit(storage, ExitRuntime.camera(), true);
		return collected.size();
	}

	@Override
	public void flushBatch() {
		RenderSystem.assertOnRenderThread();
		if (ExitRuntime.passesRunning() && !ExitRuntime.inShadowPass() && !Gpu.isInRenderPass()) {
			this.drawBatch();
		}
	}

	/** Renders this frame's batch into the channel's target, or into the main target when not routed. */
	void drawBatch() {
		SubmitNodeStorage submitted = this.batch;
		this.batch = null;
		if (submitted == null) {
			return;
		}
		boolean routed = ExitRuntime.begin(this, null);
		try {
			FeatureDrawer.draw(submitted, routed ? this.prepareForDraw() : Gpu.mainTarget());
		} finally {
			ExitRuntime.end();
		}
	}

	/** The channel's target. It is cleared and seeded with the scene depth on its first use in a frame. */
	RenderTarget prepareForDraw() {
		TextureTarget output = this.ensureTarget();
		if (!this.preparedThisFrame) {
			Gpu.clear(output);
			DepthSeed.seed(Gpu.mainTarget(), output, this.options);
			this.preparedThisFrame = true;
		}
		this.usedThisFrame = true;
		return output;
	}

	private TextureTarget ensureTarget() {
		int width = Gpu.sceneWidth();
		int height = Gpu.sceneHeight();
		if (this.target == null) {
			this.target = Gpu.colorDepthTarget("emergency_exit_" + this.id.getNamespace() + "_" + this.id.getPath().replace('/', '_'), width, height);
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
		TextureTarget output = this.ensureTarget();
		if (!this.usedThisFrame) {
			Gpu.clear(output);
		}
		DebugView.draw(main, output);
	}

	void endFrame() {
		this.usedLastFrame = this.usedThisFrame;
		this.preparedThisFrame = false;
		this.usedThisFrame = false;
		this.batch = null;
	}

	void close() {
		if (this.target != null) {
			this.target.destroyBuffers();
			this.target = null;
		}
		this.preparedThisFrame = false;
		this.usedThisFrame = false;
		this.batch = null;
	}
}
