package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
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
	 * Draws the translucent phases of {@code frame} again into {@code depth}. The colour of {@code colorTarget} is
	 * attached, since the pipelines expect one, but nothing is written to it.
	 */
	static void record(FeatureRenderDispatcher.PreparedFrame frame, RenderTarget colorTarget, RenderTarget depth) {
		recording = true;
		try {
			FeatureDrawer.executeInto(Objects.requireNonNull(colorTarget.getColorTextureView(), "target without colour"), depth.getDepthTextureView(), () -> {
				frame.executeTranslucent();
				frame.executeTranslucentAfterTerrain();
			});
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
				original.getBindGroupLayouts(),
				Arrays.stream(original.getColorTargetStates())
					.map(state -> state == null ? null : new ColorTargetState(state.blendFunction(), state.format(), ColorTargetState.WRITE_NONE))
					.toArray(ColorTargetState[]::new),
				depthWriting(original.getDepthStencilState()), original.getPolygonMode(), original.isCull(),
				original.getVertexFormatBindings(), original.getPrimitiveTopology(), original.getSortKey());
		}

		private static Identifier variantId(Identifier location) {
			return ExitMod.id("translucent_depth/" + location.getNamespace() + "/" + location.getPath());
		}

		/** The original depth test, or the default one if it had none, with depth writing on. */
		private static DepthStencilState depthWriting(DepthStencilState original) {
			if (original == null || original.depthTest() == CompareOp.ALWAYS_PASS) {
				return new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), true);
			}
			return new DepthStencilState(original.depthTest(), true, original.depthBiasScaleFactor(), original.depthBiasConstant());
		}
	}
}
