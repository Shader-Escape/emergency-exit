package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import ml.mypals.exit.api.ExitContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

/** The context of one immediate drawing. Nested drawings on the same channel share it. */
final class DrawContext implements ExitContext {
	final Channel channel;
	private final SubmitNodeCollector collector;
	/** Null when the drawing is deferred to the batch. */
	private final @Nullable RenderTarget target;
	private final boolean routed;
	private final CameraRenderState camera;
	private final PoseStack poseStack = new PoseStack();

	DrawContext(Channel channel, SubmitNodeCollector collector, @Nullable RenderTarget target, boolean routed, CameraRenderState camera) {
		this.channel = channel;
		this.collector = collector;
		this.target = target;
		this.routed = routed;
		this.camera = camera;
	}

	@Override
	public SubmitNodeCollector collector() {
		return this.collector;
	}

	@Override
	public PoseStack poseStack() {
		return this.poseStack;
	}

	@Override
	public Vec3 cameraPos() {
		return this.camera.pos;
	}

	@Override
	public CameraRenderState camera() {
		return this.camera;
	}

	@Override
	public float partialTick() {
		return this.camera.cameraEntityPartialTicks;
	}

	@Override
	public Matrix4fc modelView() {
		return ExitRuntime.viewRotation();
	}

	@Override
	public RenderTarget target() {
		if (this.target == null) {
			throw new IllegalStateException("EXIT: this drawing runs inside an open render pass and is rendered later; it has no target now");
		}
		return this.target;
	}

	@Override
	public RenderPass openPass(String label) {
		return FeatureDrawer.openPass(label, this.target());
	}

	@Override
	public boolean deferred() {
		return this.target == null;
	}

	@Override
	public boolean routed() {
		return this.routed;
	}
}
