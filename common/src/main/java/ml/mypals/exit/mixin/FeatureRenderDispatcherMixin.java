package ml.mypals.exit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.exit.impl.FeatureDrawer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** In 1.21.11 the dispatcher empties its storage after drawing it. EXIT draws some storages twice. */
@Mixin(FeatureRenderDispatcher.class)
public class FeatureRenderDispatcherMixin {
	@WrapOperation(method = "renderAllFeatures", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeStorage;clear()V"))
	private void emergency_exit$keepSubmits(SubmitNodeStorage storage, Operation<Void> original) {
		if (!FeatureDrawer.keepSubmits) {
			original.call(storage);
		}
	}
}
