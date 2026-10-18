package ml.mypals.exit.mixin.iris;

import ml.mypals.exit.impl.SceneDepth;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris for 1.16.5 has one pipeline for every pack (fixed-function rendering): the scene depth is captured where it
 * leaves the frame, before the translucent surfaces and before the hand are drawn.
 */
@Mixin(value = DeferredWorldRenderingPipeline.class, remap = false)
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
