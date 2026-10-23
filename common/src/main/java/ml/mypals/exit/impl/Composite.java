package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import ml.mypals.exit.api.ExitOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges a channel's target onto the main colour. The shader pack draws water, glass and other translucent surfaces
 * without the channel, so the channel can only be drawn over them. Parts behind such a surface are dimmed instead. The
 * surface is found by comparing the final main depth with the depth from before translucent surfaces.
 */
final class Composite {
	private static final Map<String, Gpu.Pipeline> PIPELINES = new HashMap<>();

	private Composite() {}

	static void draw(RenderTarget main, RenderTarget channel, ExitOptions options) {
		// With a resolution scaling mod the channel has the scene size, while the main colour may cover the whole window.
		// The shader then scales the coordinates.
		boolean stretched = SceneSize.mergeStretched();
		boolean hand = options.hideUnderHand();
		int dimPercent = Math.round(options.dimBehindTranslucent() * 100);
		boolean dim = dimPercent < 100;
		boolean occluded = options.occludedByWorld();

		RenderTarget opaque = SceneDepth.opaque();
		RenderTarget noHand = SceneDepth.noHand();
		if (opaque == null) {
			opaque = SceneDepth.current(main);
		}
		if (noHand == null) {
			noHand = opaque;
		}

		// Bind only the textures this variant reads. The shader declares the same ones, and each must be bound.
		List<Gpu.Sampler> samplers = new ArrayList<>();
		samplers.add(Gpu.color("ExitColor", channel));
		if (stretched || dim) {
			// When stretched the shader also reads the output size from it.
			samplers.add(Gpu.depth("MainDepth", stretched ? main : SceneDepth.current(main)));
		}
		if (dim || occluded) {
			samplers.add(Gpu.depth("ExitDepth", channel));
		}
		if (hand || dim || occluded) {
			samplers.add(Gpu.depth("OpaqueDepth", opaque));
		}
		if (hand) {
			samplers.add(Gpu.depth("NoHandDepth", noHand));
		}

		String key = (stretched ? "s" : "") + (hand ? "h" : "") + (occluded ? "o" : "") + (dim ? "d" + dimPercent : "");
		Gpu.Pipeline pipeline = PIPELINES.computeIfAbsent(key, k -> {
			Map<String, Integer> defines = new LinkedHashMap<>();
			if (stretched) {
				defines.put("STRETCHED", 1);
			}
			if (hand) {
				defines.put("HIDE_UNDER_HAND", 1);
			}
			if (occluded) {
				defines.put("OCCLUDED_BY_WORLD", 1);
			}
			if (dim) {
				defines.put("DIM_PERCENT", dimPercent);
			}
			return Gpu.colorPipeline("pipeline/composite" + (k.isEmpty() ? "" : "_" + k), "core/composite", true, defines,
				samplers.stream().map(Gpu.Sampler::name).toArray(String[]::new));
		});

		Gpu.wholeOutput = stretched;
		try {
			Gpu.draw(pipeline, main, samplers.toArray(Gpu.Sampler[]::new));
		} finally {
			Gpu.wholeOutput = false;
		}
	}
}
