package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import ml.mypals.exit.api.ExitOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds a channel target's depth, which decides what hides the channel. With occludedByWorld it starts from the scene
 * depth before translucent surfaces. With hideUnderHand the pixels of the first-person hand are set to the nearest
 * depth, so the hand stays in front.
 */
final class DepthSeed {
	private static final Map<String, Gpu.Pipeline> PIPELINES = new HashMap<>();

	private DepthSeed() {}

	static void seed(RenderTarget main, RenderTarget target, ExitOptions options) {
		boolean world = options.occludedByWorld();
		boolean hand = options.hideUnderHand();
		if (!world && !hand) {
			// The cleared depth is far and hides nothing.
			return;
		}
		RenderTarget opaque = SceneDepth.opaque();
		RenderTarget noHand = SceneDepth.noHand();
		if (opaque == null) {
			opaque = SceneDepth.current(main);
		}
		if (noHand == null) {
			noHand = opaque;
		}

		List<Gpu.Sampler> samplers = new ArrayList<>();
		samplers.add(Gpu.depth("OpaqueDepth", opaque));
		if (hand) {
			samplers.add(Gpu.depth("NoHandDepth", noHand));
		}

		String key = (world ? "w" : "") + (hand ? "h" : "");
		Gpu.Pipeline pipeline = PIPELINES.computeIfAbsent(key, k -> {
			Map<String, Integer> defines = new LinkedHashMap<>();
			if (world) {
				defines.put("OCCLUDED_BY_WORLD", 1);
			}
			if (hand) {
				defines.put("HIDE_UNDER_HAND", 1);
			}
			return Gpu.depthPipeline("pipeline/depth_seed_" + k, "core/depth_seed", defines,
				samplers.stream().map(Gpu.Sampler::name).toArray(String[]::new));
		});
		Gpu.draw(pipeline, target, samplers.toArray(Gpu.Sampler[]::new));
	}
}
