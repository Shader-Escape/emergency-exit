package ml.mypals.exit.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import ml.mypals.exit.impl.ExitRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts a render pass as closed, see {@link PortalPassMixin}. */
@Mixin(RenderPass.class)
public class RenderPassMixin {
	@Inject(method = "close", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPassBackend;close()V"))
	private void emergency_exit$passClosed(CallbackInfo ci) {
		ExitRuntime.passClosed();
	}
}
