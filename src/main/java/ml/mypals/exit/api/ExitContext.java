package ml.mypals.exit.api;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

/**
 * What an immediate drawing gets. Positions are relative to the camera, so translate the pose by
 * {@code worldPos - cameraPos()}. EXIT applies the camera rotation and projection itself.
 */
public interface ExitContext {
	/** Where to submit. It is rendered into {@link #target()} when the drawing returns. */
	SubmitNodeCollector collector();

	/** An identity pose stack at the camera position, without the camera rotation. */
	PoseStack poseStack();

	Vec3 cameraPos();

	CameraRenderState camera();

	/** The partial tick of the camera entity this frame. */
	float partialTick();

	/** The view rotation. Use it as the model-view matrix in passes you open yourself. */
	Matrix4fc modelView();

	/** The channel's target when routed, otherwise the main target. Throws when the drawing is deferred. */
	RenderTarget target();

	/**
	 * Opens a pass on {@link #target()} with the default uniforms and the level projection bound. Close it before the
	 * drawing returns. It is drawn before the submitted content. Throws when the drawing is deferred.
	 */
	RenderPass openPass(String label);

	/** Whether the submitted content is rendered later with the channel's batch. */
	boolean deferred();

	/** Whether this drawing is routed past the shader pack. */
	boolean routed();
}
