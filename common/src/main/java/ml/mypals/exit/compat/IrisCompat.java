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

	/**
	 * Whether vertex formats set up their layout in Iris's widened format, as a vertex buffer does when it is uploaded.
	 * Global, not per thread.
	 */
	public static boolean renderWithExtendedVertexFormat() {
		return LOADED && IrisHooks.renderWithExtendedVertexFormat();
	}

	public static void setRenderWithExtendedVertexFormat(boolean extended) {
		if (LOADED) {
			IrisHooks.setRenderWithExtendedVertexFormat(extended);
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

	/** The shader pack's rendering pipeline, or null. A new one is made whenever the pack or its options change. */
	public static @Nullable Object pipeline() {
		return LOADED ? IrisHooks.pipeline() : null;
	}

}
