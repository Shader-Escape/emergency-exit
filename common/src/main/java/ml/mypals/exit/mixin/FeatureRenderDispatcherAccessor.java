package ml.mypals.exit.mixin;

import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Before 26.2 a feature dispatcher renders the storage it was created with. EXIT reuses one for every storage. */
@Mixin(FeatureRenderDispatcher.class)
public interface FeatureRenderDispatcherAccessor {
	@Mutable
	@Accessor("submitNodeStorage")
	void emergency_exit$setSubmitNodeStorage(SubmitNodeStorage storage);
}
