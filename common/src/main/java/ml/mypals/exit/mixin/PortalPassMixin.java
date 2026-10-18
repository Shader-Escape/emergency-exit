package ml.mypals.exit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import ml.mypals.exit.impl.ExitRuntime;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * While a portal routes, render passes opened on the main target draw into the channel's target instead. Every
 * {@code createRenderPass} overload ends up in this method. EXIT also counts the open passes here, see
 * {@link RenderPassMixin}.
 */
@Mixin(GlCommandEncoder.class)
public class PortalPassMixin {
	@WrapMethod(method = "createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;")
	private RenderPass emergency_exit$redirectPass(Supplier<String> label, GpuTextureView color, OptionalInt clearColor,
		@Nullable GpuTextureView depth, OptionalDouble clearDepth, Operation<RenderPass> original) {
		RenderTarget target = ExitRuntime.redirectPass(color, depth);
		if (target != null) {
			RenderTarget main = ExitRuntime.mainTarget();
			if (color == main.getColorTextureView()) {
				color = target.getColorTextureView();
			}
			if (depth != null && depth == main.getDepthTextureView()) {
				depth = target.getDepthTextureView();
			}
		}
		RenderPass pass = original.call(label, color, clearColor, depth, clearDepth);
		ExitRuntime.passOpened();
		return pass;
	}
}
