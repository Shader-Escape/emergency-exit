package ml.mypals.exit.mixin.iris;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import ml.mypals.exit.compat.IrisCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Iris 1.8.1 及更早（Minecraft 1.21.3 及更早）没有 bypass 开关。重定向期间让 Iris 不再替换着色器，否则它会把通道里用原版
 * 着色器绘制的那些东西的颜色/深度写入锁掉。
 */
@Mixin(value = net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline.class, remap = false)
public class IrisShaderOverrideMixin {
	@ModifyReturnValue(method = "shouldOverrideShaders", at = @At("RETURN"))
	private boolean emergency_exit$bypass(boolean original) {
		return original && !IrisCompat.bypass();
	}

	@ModifyReturnValue(method = "getPhase", at = @At("RETURN"))
	private net.coderbot.iris.pipeline.WorldRenderingPhase emergency_exit$noPhase(net.coderbot.iris.pipeline.WorldRenderingPhase original) {
		return IrisCompat.bypass() ? net.coderbot.iris.pipeline.WorldRenderingPhase.NONE : original;
	}
}
