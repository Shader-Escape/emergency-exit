package ml.mypals.exit.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import ml.mypals.exit.impl.ExitRuntime;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21.6 之前没有 RenderSystem 的输出目标覆盖：绘制都画进 {@code getMainRenderTarget()}。重定向期间把它换成通道的目标，
 * 于是任何模组画到"主目标"的东西都会落进通道（EXIT 自己用 {@link MinecraftAccessor} 读真字段）。
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@ModifyReturnValue(method = "getMainRenderTarget", at = @At("RETURN"))
	private RenderTarget emergency_exit$routeMainTarget(RenderTarget original) {
		return ExitRuntime.routeMainTarget(original);
	}
}
