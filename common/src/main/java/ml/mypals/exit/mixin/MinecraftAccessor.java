package ml.mypals.exit.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The main target itself, which {@code getMainRenderTarget()} does not return while EXIT routes (MinecraftMixin). */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("mainRenderTarget")
	RenderTarget emergency_exit$mainRenderTarget();
}
