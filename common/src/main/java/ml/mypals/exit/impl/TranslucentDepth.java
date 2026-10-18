package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;

import java.util.Map;

/**
 * The depth of a channel's translucent drawings. Translucent render types write no depth, so without it a channel's
 * translucent pixels keep the seeded depth and lose to any other channel when channels are blended by depth. The
 * translucent render types are drawn a second time writing only depth, into a target of their own, so they do not hide
 * drawings that come later. Before merging, the nearer of both depths is folded into the channel's depth. Before 1.21.5
 * a render type sets its write masks itself, so GlStateManagerMixin forces them while recording (GlOverrides).
 */
public final class TranslucentDepth {
	private static Gpu.Pipeline fold;

	private TranslucentDepth() {}

	/** Draws the translucent render types again ({@code translucent}) into the depth of {@code depth}, writing no colour. */
	static void record(Runnable translucent, RenderTarget depth) {
		boolean saved = GlOverrides.depthOnly;
		GlOverrides.depthOnly = true;
		try {
			BatchDrawer.executeInto(depth, translucent);
		} finally {
			GlOverrides.depthOnly = saved;
		}
	}

	/** Makes the depth of {@code target} the nearer of its own and {@code depth}. */
	static void fold(RenderTarget target, RenderTarget depth) {
		if (fold == null) {
			fold = Gpu.depthPipeline("pipeline/translucent_depth_fold", "core/translucent_depth_fold", Map.of(), "TranslucentDepth");
		}
		Gpu.draw(fold, target, Gpu.depth("TranslucentDepth", depth));
	}
}
