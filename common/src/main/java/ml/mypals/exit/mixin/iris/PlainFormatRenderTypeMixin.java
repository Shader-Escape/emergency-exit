package ml.mypals.exit.mixin.iris;

import com.mojang.blaze3d.vertex.VertexFormat;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris widens {@code format()} at its return as well, the same way as the pipelines' formats. Where skipExtension is
 * set, this returns the pipeline's format, which {@link PlainFormatPipelineMixin} keeps vanilla there.
 */
@Mixin(RenderType.class)
public class PlainFormatRenderTypeMixin {
	@Inject(method = "format", at = @At("HEAD"), cancellable = true)
	private void emergency_exit$plainFormat(CallbackInfoReturnable<VertexFormat> cir) {
		if (IrisCompat.skipExtension()) {
			cir.setReturnValue(((RenderType) (Object) this).pipeline().getVertexFormatBinding(0));
		}
	}
}
