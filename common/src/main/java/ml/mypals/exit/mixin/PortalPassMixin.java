package ml.mypals.exit.mixin;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import ml.mypals.exit.impl.ExitRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a portal routes, render passes opened on the main target draw into the channel's target instead. Every
 * {@code createRenderPass} overload ends up in this method. Each encoder only knows its own open pass, so EXIT counts
 * the open passes of all encoders here.
 */
@Mixin(CommandEncoder.class)
public class PortalPassMixin {
	private static final String CREATE = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPass;";

	@ModifyVariable(method = CREATE, at = @At("HEAD"), argsOnly = true)
	private RenderPassDescriptor emergency_exit$redirectPass(RenderPassDescriptor descriptor) {
		return ExitRuntime.redirectPass(descriptor);
	}

	@Inject(method = CREATE, at = @At("RETURN"))
	private void emergency_exit$passOpened(RenderPassDescriptor descriptor, CallbackInfoReturnable<?> cir) {
		ExitRuntime.passOpened();
	}

	@Inject(method = "submitRenderPass", at = @At("HEAD"))
	private void emergency_exit$passClosed(CallbackInfo ci) {
		ExitRuntime.passClosed();
	}
}
