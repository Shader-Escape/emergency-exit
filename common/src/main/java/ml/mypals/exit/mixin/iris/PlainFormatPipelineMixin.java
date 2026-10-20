package ml.mypals.exit.mixin.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import ml.mypals.exit.compat.IrisCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the render thread renders the level, Iris hands out its widened formats from the pipelines' vertex format
 * bindings on every thread. A buffer built on another thread then gets the vanilla or the widened format depending on
 * what the render thread does at that moment. Where skipExtension is set, these return the vanilla formats, as Iris
 * for 26.3 does. Returning at the head skips Iris's callbacks at the return.
 */
@Mixin(RenderPipeline.class)
public class PlainFormatPipelineMixin {
	@Shadow
	@Final
	private VertexFormat[] vertexFormatPerBuffer;

	@Inject(method = "getVertexFormatBinding", at = @At("HEAD"), cancellable = true)
	private void emergency_exit$plainFormat(int binding, CallbackInfoReturnable<VertexFormat> cir) {
		if (IrisCompat.skipExtension()) {
			cir.setReturnValue(this.vertexFormatPerBuffer[binding]);
		}
	}

	@Inject(method = "getVertexFormatBindings", at = @At("HEAD"), cancellable = true)
	private void emergency_exit$plainFormats(CallbackInfoReturnable<VertexFormat[]> cir) {
		if (IrisCompat.skipExtension()) {
			cir.setReturnValue(this.vertexFormatPerBuffer);
		}
	}
}
