package ml.mypals.exit.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.exit.api.ExitContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

/** The context of one drawing. Nested drawings on the same channel share it. */
final class DrawContext implements ExitContext {
	final Channel channel;
	private final SubmitNodeCollector collector;
	private final boolean routed;
	private final CameraRenderState camera;
	private final PoseStack poseStack = new PoseStack();

	DrawContext(Channel channel, SubmitNodeCollector collector, boolean routed, CameraRenderState camera) {
		this.channel = channel;
		this.collector = collector;
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
		return ExitRuntime.partialTick();
	}

	@Override
	public Matrix4fc modelView() {
		return ExitRuntime.viewRotation();
	}

	@Override
	public boolean routed() {
		return this.routed;
	}
}
