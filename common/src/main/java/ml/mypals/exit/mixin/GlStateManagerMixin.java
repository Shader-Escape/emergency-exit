package ml.mypals.exit.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import ml.mypals.exit.impl.GlOverrides;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forces the GL state described in {@link GlOverrides}. */
@Mixin(GlStateManager.class)
public class GlStateManagerMixin {
	private static final int ONE = 1;
	private static final int ONE_MINUS_SRC_ALPHA = 0x0303;

	@ModifyVariable(method = "_blendFuncSeparate", at = @At("HEAD"), ordinal = 2, argsOnly = true)
	private static int emergency_exit$sourceAlpha(int factor) {
		return GlOverrides.premultipliedAlpha() ? ONE : factor;
	}

	@ModifyVariable(method = "_blendFuncSeparate", at = @At("HEAD"), ordinal = 3, argsOnly = true)
	private static int emergency_exit$destinationAlpha(int factor) {
		return GlOverrides.premultipliedAlpha() ? ONE_MINUS_SRC_ALPHA : factor;
	}

	@Inject(method = "_blendFunc", at = @At("HEAD"), cancellable = true)
	private static void emergency_exit$separateAlpha(int source, int destination, CallbackInfo ci) {
		if (GlOverrides.premultipliedAlpha()) {
			GlStateManager._blendFuncSeparate(source, destination, ONE, ONE_MINUS_SRC_ALPHA);
			ci.cancel();
		}
	}

	@Inject(method = "_colorMask", at = @At("HEAD"), cancellable = true)
	private static void emergency_exit$noColour(boolean red, boolean green, boolean blue, boolean alpha, CallbackInfo ci) {
		if (GlOverrides.depthOnly() && (red || green || blue || alpha)) {
			GlStateManager._colorMask(false, false, false, false);
			ci.cancel();
		}
	}

	@Inject(method = "_depthMask", at = @At("HEAD"), cancellable = true)
	private static void emergency_exit$writeDepth(boolean write, CallbackInfo ci) {
		if (GlOverrides.depthOnly() && !write) {
			GlStateManager._depthMask(true);
			ci.cancel();
		}
	}
}
