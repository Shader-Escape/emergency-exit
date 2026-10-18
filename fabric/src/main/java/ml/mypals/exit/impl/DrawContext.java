package ml.mypals.exit.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.exit.api.ExitContext;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

/** The context of one drawing. Nested drawings on the same channel share it. */
final class DrawContext implements ExitContext {
	final Channel channel;
	private final MultiBufferSource bufferSource;
	private final boolean routed;
	private final Camera camera;
	private final PoseStack poseStack = new PoseStack();

	DrawContext(Channel channel, MultiBufferSource bufferSource, boolean routed, Camera camera) {
		this.channel = channel;
		this.bufferSource = bufferSource;
		this.routed = routed;
		this.camera = camera;
	}

	@Override
	public MultiBufferSource bufferSource() {
		return this.bufferSource;
	}

	@Override
	public PoseStack poseStack() {
		return this.poseStack;
	}

	@Override
	public Vec3 cameraPos() {
		return this.camera.getPosition();
	}

	@Override
	public Camera camera() {
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
