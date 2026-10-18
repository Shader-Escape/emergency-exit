package ml.mypals.exit.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import ml.mypals.exit.impl.ExitRuntime;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Before 1.21.5 there are no render passes to move: everything draws into the bound framebuffer, and vanilla binds the
 * main target through this getter. While EXIT routes, it hands out the channel's target, as LitematicaPass does.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@ModifyReturnValue(method = "getMainRenderTarget", at = @At("RETURN"))
	private RenderTarget emergency_exit$routeMainTarget(RenderTarget original) {
		return ExitRuntime.targetFor(original);
	}
}
