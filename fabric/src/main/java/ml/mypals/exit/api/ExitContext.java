package ml.mypals.exit.api;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

/**
 * What a drawing gets. Positions are relative to the camera, so translate the pose by
 * {@code worldPos - cameraPos()}.
 */
public interface ExitContext {
	/** Where to submit. It is rendered with the channel's batch. */
	MultiBufferSource bufferSource();

	PoseStack poseStack();

	Vec3 cameraPos();

	Camera camera();

	/** The partial tick of the camera entity this frame. */
	float partialTick();

	Matrix4fc modelView();

	/** Whether this drawing is routed past the shader pack. */
	boolean routed();
}
