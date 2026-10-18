package ml.mypals.exit.mixin.iris;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import ml.mypals.exit.compat.IrisCompat;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Iris 1.2 and older (Minecraft 1.17 to 1.18.1) replace the vanilla terrain and entity shaders whenever a pipeline
 * exists, without asking the pipeline; while routing, Iris must find no pipeline at all. The pipeline itself is
 * untouched: while bypassing it is simply not handed out.
 */
@Mixin(value = net.coderbot.iris.pipeline.PipelineManager.class, remap = false)
public class IrisShaderOverrideMixin {
	@ModifyReturnValue(method = "getPipelineNullable", at = @At("RETURN"))
	private WorldRenderingPipeline emergency_exit$bypass(WorldRenderingPipeline original) {
		return IrisCompat.bypass() ? null : original;
	}
}
