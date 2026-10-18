package ml.mypals.exit.compat;

import ml.mypals.exit.mixin.iris.LegacyIrisPipelineAccessor;
import net.coderbot.iris.Iris;
import net.coderbot.iris.vertices.ImmediateState;
import net.irisshaders.iris.api.v0.IrisApi;

final class IrisHooks {
	static boolean isPackInUse() {
		// Iris 1.4 for 1.16.5 has no Iris.isPackInUseQuick; the public API answers it.
		return IrisApi.getInstance().isShaderPackInUse();
	}
	static boolean isRenderingShadowPass() {
		return IrisApi.getInstance().isRenderingShadowPass();
	}
	// Iris 1.8.1 and older have no bypass switch of their own; IrisShaderOverrideMixin reads ours.
	private static boolean bypass;
	static boolean bypass() {
		return bypass;
	}
	static void setBypass(boolean bypass) {
		IrisHooks.bypass = bypass;
		// Fixed-function Iris (1.16.5) only binds its programs while it believes the main target is bound. Tell it
		// which target is bound for writing, or it would shade the channel target we are routing into.
		Iris.getPipelineManager().getPipeline().ifPresent(pipeline -> pipeline.getRenderTargetStateListener().setIsMainBound(!bypass));
	}
	// Iris for 1.16.5 hands out its widened terrain and entity formats as the vanilla ones, so buffers and vanilla
	// pipelines already agree. Skipping the extension would leave the buffers in that format without its extra data,
	// so the switch does nothing here.
	static final boolean HAS_SKIP_EXTENSION = false;

	static boolean skipExtension() {
		return false;
	}
	static void setSkipExtension(boolean skip) {
	}

	// Iris for 1.16.5 decides whether to widen the vertex formats it builds by this static switch, the same one it
	// uses for the level itself.
	static boolean isRenderingLevel() {
		return ImmediateState.isRenderingLevel;
	}

	static void setRenderingLevel(boolean renderingLevel) {
		ImmediateState.isRenderingLevel = renderingLevel;
	}

	/**
	 * Iris for 1.16.5 keeps the scene depth in a texture of its own instead of the main depth: its pipeline holds the
	 * targets it renders into, and this build hands out the depth texture's id.
	 */
	static int sceneDepthTexture() {
		if (!(Iris.getPipelineManager().getPipelineNullable() instanceof LegacyIrisPipelineAccessor pipeline)) {
			return 0;
		}
		return pipeline.emergency_exit$renderTargets().getDepthTexture();
	}

	static Object pipeline() {
		return Iris.getPipelineManager().getPipelineNullable();
	}
}
