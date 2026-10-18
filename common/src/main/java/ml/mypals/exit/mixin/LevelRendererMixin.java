package ml.mypals.exit.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import ml.mypals.exit.impl.ExitRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the level rendering for {@link ExitRuntime} and merges the channels at its end. The render signature changed
 * across versions, so nothing is captured; the camera, view rotation and projection are read from the game and
 * {@link RenderSystem} instead.
 */
@Mixin(value = LevelRenderer.class, priority = 2000)
public class LevelRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void emergency_exit$beginLevel(CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		ExitRuntime.beginLevel(client.gameRenderer.getMainCamera(), new Matrix4f(RenderSystem.getModelViewMatrix()),
			new Matrix4f(RenderSystem.getProjectionMatrix()), client.getFrameTime());
	}

	@Inject(method = "renderLevel", at = @At("RETURN"))
	private void emergency_exit$endLevel(CallbackInfo ci) {
		ExitRuntime.endLevel();
	}
}
