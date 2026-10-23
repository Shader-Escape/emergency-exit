package ml.mypals.exit.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.exit.impl.ExitRuntime;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import com.mojang.math.Matrix4f;
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

	/** Before 1.21.2 there is no frame graph: this frame's depth starts when the level clears the main target. */
	@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V",
		ordinal = 0, shift = At.Shift.AFTER))
	private void emergency_exit$beginPasses(CallbackInfo ci) {
		ExitRuntime.beginPasses();
	}

	@Inject(method = "renderLevel", at = @At("RETURN"))
	private void emergency_exit$endLevel(CallbackInfo ci) {
		ExitRuntime.endLevel();
	}
}
