package ml.mypals.exit.smoke.mixin;

import ml.mypals.exit.smoke.SmokeTest;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the shadow pass check once Iris has started its shadow pass. */
@Mixin(value = ShadowRenderer.class, remap = false)
public class ShadowRendererMixin {
	@Inject(method = "renderShadows", at = @At(value = "FIELD", target = "Lnet/irisshaders/iris/shadows/ShadowRenderer;ACTIVE:Z", opcode = org.objectweb.asm.Opcodes.PUTSTATIC, shift = At.Shift.AFTER, ordinal = 0))
	private void emergency_exit_smoke$inShadowPass(CallbackInfo ci) {
		SmokeTest.inShadowPass();
	}
}
