package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import ml.mypals.exit.compat.IrisCompat;
import org.jetbrains.annotations.Nullable;

/**
 * Copies of the scene depth taken at two moments of the shader pack's frame. One is taken before the first-person hand
 * is drawn, the other before translucent surfaces are drawn. All channels share them. On this version the pack always
 * renders at the scene resolution, so the copies need no resampling (no Super Resolution here).
 */
public final class SceneDepth {
	private static TextureTarget noHand;
	private static TextureTarget opaque;
	private static boolean noHandReady;
	private static boolean opaqueReady;

	private SceneDepth() {}

	/** The scene depth before the first-person hand is drawn. */
	public static void captureBeforeHand() {
		if (ExitRuntime.wantsSceneDepth()) {
			noHand = copy(noHand, "emergency_exit_depth_no_hand");
			noHandReady = true;
		}
	}

	/** The scene depth with the hand, before translucent surfaces are drawn. */
	public static void captureBeforeTranslucents() {
		if (ExitRuntime.wantsSceneDepth()) {
			opaque = copy(opaque, "emergency_exit_depth_opaque");
			opaqueReady = true;
		}
	}

	/**
	 * Brings the shader pack's scene depth into the main depth where they are not the same (Iris 1.2 and older keep it
	 * in their own texture); call before reading the main depth.
	 */
	static void syncMainDepth() {
		int texture = IrisCompat.sceneDepthTexture();
		RenderTarget main = Gpu.mainTarget();
		if (texture != 0 && texture != main.getDepthTextureId()) {
			Gpu.copyDepthTexture(texture, main);
		}
	}

	private static TextureTarget copy(@Nullable TextureTarget target, String label) {
		syncMainDepth();
		RenderTarget main = Gpu.mainTarget();
		int width = Gpu.sceneWidth();
		int height = Gpu.sceneHeight();
		if (target == null) {
			target = Gpu.depthTarget(label, width, height);
		} else if (target.width != width || target.height != height) {
			Gpu.resize(target, width, height);
		}
		Gpu.copyDepth(target, main);
		return target;
	}

	/** Depth without translucent surfaces, or null if it was not captured this frame. */
	@Nullable
	static RenderTarget opaque() {
		return opaqueReady ? opaque : null;
	}

	/** Depth without translucent surfaces and without the hand, or null if it was not captured this frame. */
	@Nullable
	static RenderTarget noHand() {
		return noHandReady ? noHand : null;
	}

	/** The depth of {@code main} now. The pack renders at the scene resolution, so it is {@code main} itself. */
	static RenderTarget current(RenderTarget main) {
		return main;
	}

	static void endFrame() {
		noHandReady = false;
		opaqueReady = false;
	}

	static void close() {
		if (noHand != null) {
			noHand.destroyBuffers();
			noHand = null;
		}
		if (opaque != null) {
			opaque.destroyBuffers();
			opaque = null;
		}
		endFrame();
	}
}
