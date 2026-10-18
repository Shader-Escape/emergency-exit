package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;

import java.util.Map;

/**
 * The depth of a channel's translucent drawings. Translucent render types write no depth, so without it a channel's
 * translucent pixels keep the seeded depth and lose to any other channel when channels are blended by depth. The
 * batch (see {@link FeatureDrawer}) draws its translucent submits a second time, colour off and depth on, into a target
 * of its own; before merging, the nearer of both depths is folded into the channel's depth.
 */
public final class TranslucentDepth {
	private static Gpu.Pipeline fold;

	private TranslucentDepth() {}

	/** Makes the depth of {@code target} the nearer of its own and {@code depth}. */
	static void fold(RenderTarget target, RenderTarget depth) {
		if (fold == null) {
			fold = Gpu.depthPipeline("pipeline/translucent_depth_fold", "core/translucent_depth_fold", Map.of(), "TranslucentDepth");
		}
		Gpu.draw(fold, target, Gpu.depth("TranslucentDepth", depth));
	}
}
