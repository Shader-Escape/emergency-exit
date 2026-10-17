package ml.mypals.exit.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import ml.mypals.exit.impl.ExitRuntime;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 2000)
public class LevelRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void emergency_exit$beginLevel(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline,
		CameraRenderState cameraState, Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky,
		ChunkSectionsToRender chunkSectionsToRender, CallbackInfo ci) {
		ExitRuntime.beginLevel(cameraState, deltaTracker.getGameTimeDeltaPartialTick(false));
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
