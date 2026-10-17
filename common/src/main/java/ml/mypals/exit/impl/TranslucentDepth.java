package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The depth of a channel's translucent drawings. Translucent pipelines write no depth, so without it a channel's
 * translucent pixels keep the seeded depth and lose to any other channel when channels are blended by depth. The
 * translucent phases are drawn a second time with pipelines that only write depth, into a target of their own, so they
 * do not hide drawings that come later. Before merging, the nearer of both depths is folded into the channel's depth.
 */
public final class TranslucentDepth {
	private static final Map<RenderPipeline, RenderPipeline> VARIANTS = new IdentityHashMap<>();
	private static Gpu.Pipeline fold;
	private static boolean recording;

	private TranslucentDepth() {}

	/** The pipeline to draw with. While recording it is a copy of {@code pipeline} that only writes depth. */
	public static RenderPipeline swap(RenderPipeline pipeline) {
		if (!recording || pipeline instanceof DepthWriting) {
			return pipeline;
		}
		return VARIANTS.computeIfAbsent(pipeline, DepthWriting::new);
	}

	/**
	 * Draws the translucent features again ({@code translucent}) into {@code depth}. The colour of {@code colorTarget} is
	 * attached, since the pipelines expect one, but nothing is written to it.
	 */
	static void record(Runnable translucent, RenderTarget colorTarget, RenderTarget depth) {
		recording = true;
		try {
			FeatureDrawer.executeInto(Objects.requireNonNull(colorTarget.getColorTextureView(), "target without colour"), depth.getDepthTextureView(), translucent);
		} finally {
			recording = false;
		}
	}

	/** Makes the depth of {@code target} the nearer of its own and {@code depth}. */
	static void fold(RenderTarget target, RenderTarget depth) {
		if (fold == null) {
			fold = Gpu.depthPipeline("pipeline/translucent_depth_fold", "core/translucent_depth_fold", Map.of(), "TranslucentDepth");
		}
		Gpu.draw(fold, target, Gpu.depth("TranslucentDepth", depth));
	}

	/** A copy of a pipeline that writes depth and no colour. */
	private static final class DepthWriting extends RenderPipeline {
		DepthWriting(RenderPipeline original) {
			super(variantId(original.getLocation()), original.getVertexShader(), original.getFragmentShader(), original.getShaderDefines(),
				original.getSamplers(), original.getUniforms(), original.getBlendFunction(), depthTest(original.getDepthTestFunction()),
				original.getPolygonMode(), original.isCull(), false, false, true, original.getColorLogic(), original.getVertexFormat(),
				original.getVertexFormatMode(), original.getDepthBiasScaleFactor(), original.getDepthBiasConstant(), original.getSortKey());
		}

		private static Identifier variantId(Identifier location) {
			return ExitMod.id("translucent_depth/" + location.getNamespace() + "/" + location.getPath());
		}

		/** The original depth test, or the default one if it had none. Without a depth test nothing writes depth. */
		private static DepthTestFunction depthTest(DepthTestFunction original) {
			return original == DepthTestFunction.NO_DEPTH_TEST ? DepthTestFunction.LEQUAL_DEPTH_TEST : original;
		}
	}
}
