package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import ml.mypals.exit.api.ExitOptions;
import org.jetbrains.annotations.Nullable;

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

	private static RenderTarget scratch;

	private DepthSeed() {}

	static boolean seeds(ExitOptions options) {
		return options.occludedByWorld() || options.hideUnderHand();
	}

	static void seed(RenderTarget main, RenderTarget target, ExitOptions options) {
		if (!seeds(options)) {
			// The cleared depth is far and hides nothing.
			return;
		}
		draw(main, target, options, null);
	}

	/** A copy of the depth {@code target} was seeded with. */
	static RenderTarget remember(@Nullable RenderTarget copy, RenderTarget target, String label) {
		copy = sized(copy, label, target);
		Gpu.copyDepth(copy, target);
		return copy;
	}

	/** Seeds the pixels of {@code target} whose depth is still {@code seeded} again, from the scene depth now. */
	static void reseed(RenderTarget main, RenderTarget target, RenderTarget seeded, ExitOptions options) {
		scratch = sized(scratch, "emergency_exit_reseed_scratch", target);
		Gpu.copyDepth(scratch, target);
		draw(main, target, options, seeded);
	}

	private static RenderTarget sized(@Nullable RenderTarget copy, String label, RenderTarget like) {
		if (copy == null) {
			return Gpu.depthTarget(label, like.width, like.height);
		}
		if (copy.width != like.width || copy.height != like.height) {
			Gpu.resize(copy, like.width, like.height);
		}
		return copy;
	}

	private static void draw(RenderTarget main, RenderTarget target, ExitOptions options, @Nullable RenderTarget seeded) {
		boolean again = seeded != null;
		boolean world = options.occludedByWorld();
		boolean hand = options.hideUnderHand();
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
		if (again) {
			samplers.add(Gpu.depth("ChannelDepth", scratch));
			samplers.add(Gpu.depth("SeededDepth", seeded));
		}

		String key = (world ? "w" : "") + (hand ? "h" : "") + (again ? "r" : "");
		Gpu.Pipeline pipeline = PIPELINES.computeIfAbsent(key, k -> {
			Map<String, Integer> defines = new LinkedHashMap<>();
			if (world) {
				defines.put("OCCLUDED_BY_WORLD", 1);
			}
			if (hand) {
				defines.put("HIDE_UNDER_HAND", 1);
			}
			if (again) {
				defines.put("RESEED", 1);
			}
			return Gpu.depthPipeline("pipeline/depth_seed_" + k, "core/depth_seed", defines,
				samplers.stream().map(Gpu.Sampler::name).toArray(String[]::new));
		});
		Gpu.draw(pipeline, target, samplers.toArray(Gpu.Sampler[]::new));
	}
}
