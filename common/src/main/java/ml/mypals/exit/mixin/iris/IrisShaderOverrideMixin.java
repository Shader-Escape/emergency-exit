package ml.mypals.exit.mixin.iris;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import ml.mypals.exit.compat.IrisCompat;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Iris 1.8 locks the colour and depth writes of a vanilla program it does not know whenever it would override shaders,
 * bypass or not, so EXIT's own pipelines (and entity pipelines it draws with) would draw nothing. While the bypass is
 * on Iris must not want to override. LitematicaPass does the same before 26.3.
 */
@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class IrisShaderOverrideMixin {
	@ModifyReturnValue(method = "shouldOverrideShaders", at = @At("RETURN"))
	private boolean emergency_exit$bypass(boolean original) {
		return original && !IrisCompat.bypass();
	}
}
