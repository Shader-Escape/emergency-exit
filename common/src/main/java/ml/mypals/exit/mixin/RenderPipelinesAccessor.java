package ml.mypals.exit.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Registers EXIT's pipelines like vanilla's own, which keeps them across resource reloads. */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
	@Invoker("register")
	static RenderPipeline emergency_exit$register(RenderPipeline pipeline) {
		throw new AssertionError();
	}
}
