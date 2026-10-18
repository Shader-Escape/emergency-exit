package ml.mypals.exit.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Before 1.21.6 {@link MinecraftMixin} makes {@code getMainRenderTarget()} hand out the channel's target while routing;
 * EXIT itself needs the real one and reads this field.
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("mainRenderTarget")
	RenderTarget emergency_exit$mainRenderTarget();
}
