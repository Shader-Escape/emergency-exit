package ml.mypals.exit.mixin.iris;

import net.coderbot.iris.rendertarget.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Iris 1.2 and older for Minecraft 1.18.1 and below keep the scene depth in a texture of their own instead of the main
 * depth (see {@code IrisHooks.sceneDepthTexture}). Their pipeline holds the targets it renders into. Applied only when
 * Iris is loaded (see ExitMixinPlugin).
 */
@Mixin(targets = "net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline", remap = false)
public interface LegacyIrisPipelineAccessor {
	@Accessor("renderTargets")
	RenderTargets emergency_exit$renderTargets();
}
