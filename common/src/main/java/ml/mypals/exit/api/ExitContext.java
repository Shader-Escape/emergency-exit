package ml.mypals.exit.api;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

/**
 * What a drawing gets. Positions are relative to the camera, so translate the pose by
 * {@code worldPos - cameraPos()}. EXIT applies the camera rotation and projection itself.
 */
public interface ExitContext {
	/** Where to submit. It is rendered with the channel's batch. */
	SubmitNodeCollector collector();

	/** An identity pose stack at the camera position, without the camera rotation. */
	PoseStack poseStack();

	Vec3 cameraPos();

	CameraRenderState camera();

	/** The partial tick of the camera entity this frame. */
	float partialTick();

	/** The view rotation, the model-view matrix EXIT renders the submits with. */
	Matrix4fc modelView();

	/** Whether this drawing is routed past the shader pack. */
	boolean routed();
}
