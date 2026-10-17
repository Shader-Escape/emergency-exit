package ml.mypals.exit.mixin;

import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import ml.mypals.exit.impl.ExitRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * While a portal routes, render passes opened on the main target draw into the channel's target instead. Every
 * {@code createRenderPass} overload ends up in this method.
 */
@Mixin(FrontendCommandEncoder.class)
public class PortalPassMixin {
	@ModifyVariable(method = "createRenderPass(Lcom/mojang/renderpearl/api/commands/RenderPassDescriptor;)Lcom/mojang/renderpearl/api/commands/RenderPass;",
		at = @At("HEAD"), argsOnly = true)
	private RenderPassDescriptor emergency_exit$redirectPass(RenderPassDescriptor descriptor) {
		return ExitRuntime.redirectPass(descriptor);
	}
}
