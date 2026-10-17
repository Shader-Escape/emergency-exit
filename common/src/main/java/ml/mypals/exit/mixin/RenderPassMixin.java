package ml.mypals.exit.mixin;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import ml.mypals.exit.impl.ExitRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts a render pass as closed, see {@link PortalPassMixin}. */
@Mixin(GlCommandEncoder.class)
public class RenderPassMixin {
	@Inject(method = "finishRenderPass", at = @At("HEAD"))
	private void emergency_exit$passClosed(CallbackInfo ci) {
		ExitRuntime.passClosed();
	}
}
