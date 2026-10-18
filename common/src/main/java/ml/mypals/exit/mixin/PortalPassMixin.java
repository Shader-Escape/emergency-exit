package ml.mypals.exit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTexture;
import ml.mypals.exit.impl.ExitRuntime;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Moves render passes opened on the main target. Every {@code createRenderPass} overload ends up in this method.
 * <ul>
 *     <li>While EXIT renders a batch, passes on the main target draw into the batch's target. 1.21.6 has
 *     {@code RenderSystem.outputColorTextureOverride} for this; 1.21.5 has nothing, but every render type opens its own
 *     pass on the main target.</li>
 *     <li>While a portal routes, passes on the main target draw into the channel's target instead.</li>
 * </ul>
 * EXIT also counts the open passes here, see {@link RenderPassMixin}.
 */
@Mixin(GlCommandEncoder.class)
public class PortalPassMixin {
	@WrapMethod(method = "createRenderPass(Lcom/mojang/blaze3d/textures/GpuTexture;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTexture;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;")
	private RenderPass emergency_exit$redirectPass(GpuTexture color, OptionalInt clearColor, @Nullable GpuTexture depth,
		OptionalDouble clearDepth, Operation<RenderPass> original) {
		RenderTarget main = ExitRuntime.mainTarget();
		GpuTexture outputColor = ExitRuntime.outputColor();
		if (outputColor != null && color == main.getColorTexture()) {
			color = outputColor;
			GpuTexture outputDepth = ExitRuntime.outputDepth();
			if (outputDepth != null && depth == main.getDepthTexture()) {
				depth = outputDepth;
			}
		}
		RenderTarget target = ExitRuntime.redirectPass(color, depth);
		if (target != null) {
			if (color == main.getColorTexture()) {
				color = target.getColorTexture();
			}
			if (depth != null && depth == main.getDepthTexture()) {
				depth = target.getDepthTexture();
			}
		}
		RenderPass pass = original.call(color, clearColor, depth, clearDepth);
		ExitRuntime.passOpened();
		return pass;
	}
}
