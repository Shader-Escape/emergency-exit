package ml.mypals.exit.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
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
	// Iris 1.9.6 widens a new BufferBuilder's format while the level renders unless skipExtension is set on its
	// thread, and hands out the widened formats from RenderPipeline.getVertexFormat() then, on every thread.
	// PlainFormatPipelineMixin keeps the vanilla formats there where skipExtension is set, so buffers built with it
	// match the pipelines' vanilla formats.
	static final boolean HAS_SKIP_EXTENSION = true;

	static boolean skipExtension() {
		return ImmediateState.skipExtension.get();
	}
	static void setSkipExtension(boolean skip) {
		ImmediateState.skipExtension.set(skip);
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
