package ml.mypals.exit.impl;

/**
 * GL state EXIT forces while vanilla code draws for it (GlStateManagerMixin). Before 1.21.5 a render type sets the
 * blending and write masks itself when it is drawn, so EXIT cannot hand it a pipeline of its own.
 * <ul>
 *     <li>While something draws into a channel, the alpha blend factors are {@code (ONE, ONE_MINUS_SRC_ALPHA)}, so the
 *     channel's alpha is the coverage the merge expects (premultiplied colour), as LitematicaPass does.</li>
 *     <li>While EXIT records the depth of translucent drawings, or measures the shader pack's render scale, nothing
 *     writes colour and everything writes depth.</li>
 * </ul>
 * EXIT's own full-screen passes ({@link Gpu#draw}) are left alone.
 */
public final class GlOverrides {
	/** Counts what draws into a channel now: routing portals and batches rendered into a channel. */
	static int intoChannel;
	static boolean depthOnly;
	/** Set while {@link Gpu#draw} runs. */
	static boolean paused;

	private GlOverrides() {}

	public static boolean premultipliedAlpha() {
		return intoChannel > 0 && !paused;
	}

	public static boolean depthOnly() {
		return depthOnly && !paused;
	}
}
