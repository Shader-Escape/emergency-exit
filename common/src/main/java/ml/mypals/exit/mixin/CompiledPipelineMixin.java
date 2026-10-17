package ml.mypals.exit.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.opengl.GlRenderPass;
import ml.mypals.exit.impl.TranslucentDepth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** While EXIT records the depth of translucent drawings, every draw gets a pipeline that only writes depth. */
@Mixin(GlRenderPass.class)
public class CompiledPipelineMixin {
	@ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true)
	private RenderPipeline emergency_exit$depthOnly(RenderPipeline pipeline) {
		return TranslucentDepth.swap(pipeline);
	}
}
