package ml.mypals.exit.compat;

import ml.mypals.exit.mixin.iris.LegacyIrisPipelineAccessor;
import net.coderbot.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;

final class IrisHooks {
	static boolean isPackInUse() {
		// Iris 1.7 (1.20.6) has no Iris.isPackInUseQuick; the public API answers it.
		return IrisApi.getInstance().isShaderPackInUse();
	}
	static boolean isRenderingShadowPass() {
		return IrisApi.getInstance().isRenderingShadowPass();
	}
	// Iris 1.2 and older (Minecraft 1.18.1 and older) have no bypass switch; IrisShaderOverrideMixin reads ours.
	private static boolean bypass;
	static boolean bypass() {
		return bypass;
	}
	static void setBypass(boolean bypass) {
		IrisHooks.bypass = bypass;
	}
	// Iris 1.2 for 1.17.1 has no skipExtension switch (it never widens the entity formats), so there is nothing to set.
	static final boolean HAS_SKIP_EXTENSION = false;

	static boolean skipExtension() {
		return false;
	}
	static void setSkipExtension(boolean skip) {
	}

	// Iris 1.2 and older have no `isRenderingLevel` either: they never widen the vertex formats of the level.
	static boolean isRenderingLevel() {
		return false;
	}

	static void setRenderingLevel(boolean renderingLevel) {
	}

	/**
	 * Iris 1.2 and older keep the scene depth in a texture of their own instead of the main depth. While routing,
	 * {@link IrisShaderOverrideMixin} hides the pipeline, so ask for it with the bypass switch off; the pack's targets
	 * are the same either way.
	 */
	static int sceneDepthTexture() {
		boolean routing = bypass;
		bypass = false;
		try {
			if (!(Iris.getPipelineManager().getPipelineNullable() instanceof LegacyIrisPipelineAccessor pipeline)) {
				return 0;
			}
			// Most builds hand out their DepthTexture, some (the later ones for 1.17.1) its ID.
			Object depth = pipeline.emergency_exit$renderTargets().getDepthTexture();
			return depth instanceof Integer id ? id : textureId(depth);
		} finally {
			bypass = routing;
		}
	}

	private static java.lang.reflect.Method getTextureId;

	private static int textureId(Object depthTexture) {
		try {
			if (getTextureId == null) {
				getTextureId = depthTexture.getClass().getMethod("getTextureId");
			}
			return (int) getTextureId.invoke(depthTexture);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unexpected Iris depth texture " + depthTexture, e);
		}
	}

	static Object pipeline() {
		return Iris.getPipelineManager().getPipelineNullable();
	}
}
