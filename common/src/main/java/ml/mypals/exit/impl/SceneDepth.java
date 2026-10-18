package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import org.jspecify.annotations.Nullable;

/**
 * Copies of the scene depth taken at two moments of the shader pack's frame. One is taken before the first-person hand
 * is drawn, the other before translucent surfaces are drawn. All channels share them. When the pack renders at a lower
 * resolution, the copies are resampled to full resolution (see {@link SceneScale}), so they line up with the channels.
 */
public final class SceneDepth {
	private static TextureTarget noHand;
	private static TextureTarget opaque;
	private static TextureTarget current;
	private static boolean noHandReady;
	private static boolean opaqueReady;

	private SceneDepth() {}

	/** The scene depth before the first-person hand is drawn. */
	public static void captureBeforeHand() {
		if (ExitRuntime.wantsSceneDepth()) {
			FrameDump.depth("pack-depth-before-hand", Gpu.mainTarget());
			noHand = copy(noHand, "emergency_exit_depth_no_hand");
			FrameDump.depth(SceneScale.scaled() ? "no-hand-aligned" : "no-hand-copy", noHand);
			noHandReady = true;
			SceneScale.measureIfNeeded();
		}
	}

	/** The scene depth with the hand, before translucent surfaces are drawn. */
	public static void captureBeforeTranslucents() {
		if (ExitRuntime.wantsSceneDepth()) {
			FrameDump.depth("pack-depth-before-translucents", Gpu.mainTarget());
			opaque = copy(opaque, "emergency_exit_depth_opaque");
			FrameDump.depth(SceneScale.scaled() ? "opaque-aligned" : "opaque-copy", opaque);
			opaqueReady = true;
			// Without a first-person hand there is no beginHand.
			SceneScale.measureIfNeeded();
		}
	}

	private static TextureTarget copy(@Nullable TextureTarget target, String label) {
		return copy(target, label, Gpu.mainTarget());
	}

	private static TextureTarget copy(@Nullable TextureTarget target, String label, RenderTarget main) {
		int width = Gpu.sceneWidth();
		int height = Gpu.sceneHeight();

		if (target == null) {
			target = Gpu.depthTarget(label, width, height);
		} else if (target.width != width || target.height != height) {
			Gpu.resize(target, width, height);
		}
		if (SceneScale.scaled()) {
			SceneScale.resample(target, main);
		} else {
			Gpu.copyDepth(target, main);
		}
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

	/**
	 * The depth of {@code main} now, lined up with the channels. It is {@code main} itself unless the pack renders at a
	 * lower resolution; then it is a copy, valid until the next call.
	 */
	static RenderTarget current(RenderTarget main) {
		if (!SceneScale.scaled()) {
			return main;
		}
		current = copy(current, "emergency_exit_depth_current", main);
		return current;
	}

	static void endFrame() {
		noHandReady = false;
		opaqueReady = false;
		SceneScale.endFrame();
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
		if (current != null) {
			current.destroyBuffers();
			current = null;
		}
		SceneScale.close();
		endFrame();
	}
}
