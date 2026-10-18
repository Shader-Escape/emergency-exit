package ml.mypals.exit.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.exit.impl.ExitRuntime;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the level rendering for {@link ExitRuntime} and merges the channels at its end. The game renderer hands the
 * level its view rotation (as a pose stack) and projection: {@code renderLevel(poseStack, partialTick, nanoTime,
 * renderBlockOutline, camera, gameRenderer, lightTexture, projection)}.
 */
@Mixin(value = LevelRenderer.class, priority = 2000)
public class LevelRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void emergency_exit$beginLevel(PoseStack poseStack, float partialTick, long nanoTime, boolean renderBlockOutline,
		Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projection, CallbackInfo ci) {
		ExitRuntime.beginLevel(camera, poseStack.last().pose(), projection, partialTick);
	}

	@Inject(method = "renderLevel", at = @At("RETURN"))
	private void emergency_exit$endLevel(CallbackInfo ci) {
		ExitRuntime.endLevel();
	}
}
