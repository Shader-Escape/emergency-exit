package ml.mypals.exit.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Before 1.21.9 a render type does not say which pipeline it draws with. EXIT asks it whether it blends. */
@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public interface CompositeRenderTypeAccessor {
	@Accessor("renderPipeline")
	RenderPipeline emergency_exit$renderPipeline();
}
