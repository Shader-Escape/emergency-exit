package ml.mypals.exit.mixin.iris;

import ml.mypals.exit.impl.SceneDepth;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class IrisRenderingPipelineMixin {
	@Inject(method = "beginHand", at = @At("HEAD"), require = 0)
	private void emergency_exit$captureBeforeHand(CallbackInfo ci) {
		SceneDepth.captureBeforeHand();
	}

	@Inject(method = "beginTranslucents", at = @At("HEAD"), require = 0)
	private void emergency_exit$captureBeforeTranslucents(CallbackInfo ci) {
		SceneDepth.captureBeforeTranslucents();
	}
}
