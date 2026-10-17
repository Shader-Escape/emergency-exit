package ml.mypals.exit.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import ml.mypals.exit.impl.TranslucentDepth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** While EXIT records the depth of translucent drawings, every draw gets a pipeline that only writes depth. */
@Mixin(RenderSystem.class)
public class CompiledPipelineMixin {
	@ModifyVariable(method = "getCompiledPipeline", at = @At("HEAD"), argsOnly = true)
	private static RenderPipeline emergency_exit$depthOnly(RenderPipeline pipeline) {
		return TranslucentDepth.swap(pipeline);
	}
}
