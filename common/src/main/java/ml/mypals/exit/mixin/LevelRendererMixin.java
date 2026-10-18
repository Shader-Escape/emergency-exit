package ml.mypals.exit.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import ml.mypals.exit.impl.ExitRuntime;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 2000)
public class LevelRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void emergency_exit$beginLevel(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline,
		Camera camera, GameRenderer gameRenderer, Matrix4f modelViewMatrix, Matrix4f projection, CallbackInfo ci) {
		ExitRuntime.beginLevel(camera, modelViewMatrix, projection, deltaTracker.getGameTimeDeltaPartialTick(false));
	}

	@Inject(method = "renderLevel", at = @At(value = "INVOKE",
		target = "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V"))
	private void emergency_exit$beginPasses(CallbackInfo ci) {
		ExitRuntime.beginPasses();
	}

	@Inject(method = "renderLevel", at = @At("RETURN"))
	private void emergency_exit$endLevel(CallbackInfo ci) {
		ExitRuntime.endLevel();
	}
}
