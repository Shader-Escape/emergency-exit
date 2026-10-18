package ml.mypals.exit.mixin.iris;

import ml.mypals.exit.compat.IrisCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Fixed-function Iris (1.16.5) only binds its programs while it believes the main target is bound: it is told which
 * target is bound for writing, and shades the frame from there. While routing, the main target is not the one being
 * written to, so the switch is held down as well. {@code IrisHooks.setBypass} tells Iris the same thing.
 */
@Mixin(value = net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline.class, remap = false)
public class IrisShaderOverrideMixin {
	@ModifyVariable(method = "setIsMainBound", at = @At("HEAD"), argsOnly = true)
	private boolean emergency_exit$bypass(boolean mainBound) {
		return mainBound && !IrisCompat.bypass();
	}
}
