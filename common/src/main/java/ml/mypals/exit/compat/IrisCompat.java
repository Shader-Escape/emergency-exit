package ml.mypals.exit.compat;

import ml.mypals.exit.platform.Platform;
import org.jetbrains.annotations.Nullable;

public final class IrisCompat {
	public static final boolean LOADED = Platform.INSTANCE.isModLoaded("iris");
	public static boolean isPackInUse() {
		return LOADED && IrisHooks.isPackInUse();
	}
	public static boolean isRenderingShadowPass() {
		return LOADED && IrisHooks.isRenderingShadowPass();
	}
	public static boolean bypass() {
		return LOADED && IrisHooks.bypass();
	}
	public static void setBypass(boolean bypass) {
		if (LOADED) {
			IrisHooks.setBypass(bypass);
		}
	}
	/** Whether Iris has the skipExtension switch on this version. Without it, setting it does nothing. */
	public static boolean hasSkipExtension() {
		return LOADED && IrisHooks.HAS_SKIP_EXTENSION;
	}
	public static boolean skipExtension() {
		return LOADED && IrisHooks.skipExtension();
	}
	public static void setSkipExtension(boolean skip) {
		if (LOADED) {
			IrisHooks.setSkipExtension(skip);
		}
	}

	/** Iris only widens vertex formats while the level is being rendered. */
	public static boolean isRenderingLevel() {
		return LOADED && IrisHooks.isRenderingLevel();
	}

	public static void setRenderingLevel(boolean renderingLevel) {
		if (LOADED) {
			IrisHooks.setRenderingLevel(renderingLevel);
		}
	}

	/** Iris 1.8 also changes VAO attribute strides through a separate render-thread switch. */
	public static boolean extendedVertexFormat() {
		return LOADED && IrisHooks.extendedVertexFormat();
	}

	public static void setExtendedVertexFormat(boolean extended) {
		if (LOADED) {
			IrisHooks.setExtendedVertexFormat(extended);
		}
	}

	/** The shader pack's rendering pipeline, or null. A new one is made whenever the pack or its options change. */
	public static @Nullable Object pipeline() {
		return LOADED ? IrisHooks.pipeline() : null;
	}

}
