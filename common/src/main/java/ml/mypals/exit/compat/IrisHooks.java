package ml.mypals.exit.compat;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
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
	// Iris 1.8 (1.8.8 for 1.21.1) widens the format of every BufferBuilder made while a pack is in use, unless skipExtension
	// is set; isRenderingLevel does not matter here. The vanilla pipelines keep the vanilla formats, so the switch works
	// as on 26.3. (Iris 1.9 and later, for 1.21.6 to 26.2, hand out the widened formats as the vanilla ones instead.)
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

	static boolean extendedVertexFormat() {
		return ImmediateState.renderWithExtendedVertexFormat;
	}

	static void setExtendedVertexFormat(boolean extended) {
		ImmediateState.renderWithExtendedVertexFormat = extended;
	}

	static void setRenderingLevel(boolean renderingLevel) {
		ImmediateState.isRenderingLevel = renderingLevel;
	}

	static Object pipeline() {
		return Iris.getPipelineManager().getPipelineNullable();
	}

}
