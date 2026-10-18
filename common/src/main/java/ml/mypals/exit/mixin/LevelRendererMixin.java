package ml.mypals.exit.mixin;

import ml.mypals.exit.impl.ExitRuntime;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 2000)
public class LevelRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void emergency_exit$beginLevel(DeltaTracker deltaTracker, boolean renderOutline, Camera camera, GameRenderer gameRenderer,
		LightTexture lightTexture, Matrix4f modelViewMatrix, Matrix4f projection, CallbackInfo ci) {
		ExitRuntime.beginLevel(camera, modelViewMatrix, projection, deltaTracker.getGameTimeDeltaPartialTick(false));
	}

	/** Before 1.21.2 there is no frame graph: the level draws directly, from the clear of the main target on. */
	@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", shift = At.Shift.AFTER, ordinal = 0))
	private void emergency_exit$beginPasses(CallbackInfo ci) {
		ExitRuntime.beginPasses();
	}

	@Inject(method = "renderLevel", at = @At("RETURN"))
	private void emergency_exit$endLevel(CallbackInfo ci) {
		ExitRuntime.endLevel();
	}
}
