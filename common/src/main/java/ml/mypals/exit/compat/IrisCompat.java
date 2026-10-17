package ml.mypals.exit.compat;

import ml.mypals.exit.platform.Platform;

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
}
