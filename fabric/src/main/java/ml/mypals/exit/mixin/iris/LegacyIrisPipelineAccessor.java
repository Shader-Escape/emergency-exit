package ml.mypals.exit.mixin.iris;

import net.coderbot.iris.rendertarget.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Iris for 1.16.5 keeps the scene depth in a texture of its own instead of the main depth (see
 * {@code IrisHooks.sceneDepthTexture}). Its pipeline holds the targets it renders into. Applied only when Iris is
 * loaded (see ExitMixinPlugin).
 */
@Mixin(targets = "net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline", remap = false)
public interface LegacyIrisPipelineAccessor {
	@Accessor("renderTargets")
	RenderTargets emergency_exit$renderTargets();
}
