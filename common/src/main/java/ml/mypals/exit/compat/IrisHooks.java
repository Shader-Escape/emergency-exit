package ml.mypals.exit.compat;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.vertices.ImmediateState;

final class IrisHooks {
	static boolean isPackInUse() {
		return Iris.isPackInUseQuick();
	}
	static boolean isRenderingShadowPass() {
		return IrisApi.getInstance().isRenderingShadowPass();
	}
	static boolean bypass() {
		return ImmediateState.bypass;
	}
	static void setBypass(boolean bypass) {
		ImmediateState.bypass = bypass;
	}
	// Iris for 26.2 hands out its widened terrain and entity formats as the vanilla ones, so buffers and vanilla
	// pipelines already agree. Skipping the extension would leave the buffers in that format without its extra data
	// ("Missing elements in vertex: mc_midTexCoord, at_tangent"), so the switch does nothing here.
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

	static Object pipeline() {
		return Iris.getPipelineManager().getPipelineNullable();
	}

	static void usePackProgramOf(RenderPipeline original, RenderPipeline copy) {
		IrisPipelines.copyPipeline(original, copy);
	}
}
