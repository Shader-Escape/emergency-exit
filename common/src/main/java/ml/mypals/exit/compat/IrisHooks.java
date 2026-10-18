package ml.mypals.exit.compat;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.vertices.ImmediateState;

final class IrisHooks {
	static boolean isPackInUse() {
		// Iris 1.7 (1.20.6) has no Iris.isPackInUseQuick; the public API answers it.
		return IrisApi.getInstance().isShaderPackInUse();
	}
	static boolean isRenderingShadowPass() {
		return IrisApi.getInstance().isRenderingShadowPass();
	}
	// Iris 1.8.1 and older (Minecraft 1.21.3 and older) have no bypass switch; IrisShaderOverrideMixin reads ours.
	private static boolean bypass;
	static boolean bypass() {
		return bypass;
	}
	static void setBypass(boolean bypass) {
		IrisHooks.bypass = bypass;
	}
	// Iris for 1.20.6 hands out its widened terrain and entity formats as the vanilla ones, so buffers and vanilla
	// pipelines already agree. Skipping the extension would leave the buffers in that format without its extra data,
	// so the switch does nothing here.
	static final boolean HAS_SKIP_EXTENSION = false;

	static boolean skipExtension() {
		return false;
	}
	static void setSkipExtension(boolean skip) {
	}

	static boolean isRenderingLevel() {
		return ImmediateState.isRenderingLevel;
	}

	static void setRenderingLevel(boolean renderingLevel) {
		ImmediateState.isRenderingLevel = renderingLevel;
	}

	// Iris 1.2 and older for 1.18.1 and below keep the scene depth in their own texture; 1.20.6 uses the main depth.
	static int sceneDepthTexture() {
		return 0;
	}

	static Object pipeline() {
		return Iris.getPipelineManager().getPipelineNullable();
	}
}
