package ml.mypals.exit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = { "com.mojang.blaze3d.opengl.GlRenderPass", "com.mojang.blaze3d.vulkan.VulkanRenderPass" })
public class StrictValidationMixin {
	@ModifyExpressionValue(method = "<clinit>", at = @At(value = "FIELD", target = "Lnet/minecraft/SharedConstants;IS_RUNNING_IN_IDE:Z"), require = 0)
	private static boolean emergency_exit$disableStrictValidation(boolean original) {
		return false;
	}
}
