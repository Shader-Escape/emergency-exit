package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import ml.mypals.exit.api.ExitOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blends channels with each other by depth. Each channel is joined into an accumulated layer, and per pixel the nearer
 * one is drawn in front. The layer is then merged onto the main target. It keeps one depth per pixel, so three or more
 * channels overlapping at one pixel are only sorted roughly. Two layers take turns, since a pass cannot read the
 * texture it writes.
 */
final class ChannelBlend {
	/** The channel options were already applied when joining, so the layer is merged as is. */
	private static final ExitOptions PLAIN = ExitOptions.builder().occludedByWorld(false).hideUnderHand(false).dimBehindTranslucent(1).build();
	private static final Map<String, Gpu.Pipeline> PIPELINES = new HashMap<>();
	private static TextureTarget front;
	private static TextureTarget back;

	private ChannelBlend() {}

	/** Blends two or more channels by depth and merges the result onto {@code main}. */
	static void draw(RenderTarget main, List<Channel> channels) {
		int width = Gpu.sceneWidth();
		int height = Gpu.sceneHeight();
		front = ensure(front, "emergency_exit_blend_a", width, height);
		back = ensure(back, "emergency_exit_blend_b", width, height);
		Gpu.clear(front);
		dumpMasks(main);
		for (Channel channel : channels) {
			join(main, channel.drawnTarget(), channel.options());
			FrameDump.color("layer-after-" + channel.dumpName(), back);
			FrameDump.depth("layer-depth-after-" + channel.dumpName(), back);
			TextureTarget swap = front;
			front = back;
			back = swap;
		}
		Composite.draw(main, front, PLAIN);
	}

	/** The hand and the translucent surfaces as the joins see them, for {@link FrameDump}. */
	private static void dumpMasks(RenderTarget main) {
		RenderTarget opaque = SceneDepth.opaque();
		RenderTarget noHand = SceneDepth.noHand();
		if (FrameDump.active() && opaque != null && noHand != null) {
			FrameDump.different("hand-mask", opaque, noHand);
			FrameDump.nearer("translucent-in-front", SceneDepth.current(main), opaque);
		}
	}

	/** Writes {@code front} with {@code channel} joined into {@code back}. */
	private static void join(RenderTarget main, RenderTarget channel, ExitOptions options) {
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
		samplers.add(Gpu.color("AccColor", front));
		samplers.add(Gpu.depth("AccDepth", front));
		samplers.add(Gpu.color("ExitColor", channel));
		samplers.add(Gpu.depth("ExitDepth", channel));
		if (dim) {
			samplers.add(Gpu.depth("MainDepth", SceneDepth.current(main)));
		}
		if (hand || dim || occluded) {
			samplers.add(Gpu.depth("OpaqueDepth", opaque));
		}
		if (hand) {
			samplers.add(Gpu.depth("NoHandDepth", noHand));
		}

		String key = (hand ? "h" : "") + (occluded ? "o" : "") + (dim ? "d" + dimPercent : "");
		Gpu.Pipeline pipeline = PIPELINES.computeIfAbsent(key, k -> {
			Map<String, Integer> defines = new LinkedHashMap<>();
			if (hand) {
				defines.put("HIDE_UNDER_HAND", 1);
			}
			if (occluded) {
				defines.put("OCCLUDED_BY_WORLD", 1);
			}
			if (dim) {
				defines.put("DIM_PERCENT", dimPercent);
			}
			return Gpu.layerPipeline("pipeline/blend_layer" + (k.isEmpty() ? "" : "_" + k), "core/blend_layer", defines,
				samplers.stream().map(Gpu.Sampler::name).toArray(String[]::new));
		});
		Gpu.draw(pipeline, back, samplers.toArray(Gpu.Sampler[]::new));
	}

	private static TextureTarget ensure(TextureTarget target, String label, int width, int height) {
		if (target == null) {
			return Gpu.colorDepthTarget(label, width, height);
		}
		if (target.width != width || target.height != height) {
			Gpu.resize(target, width, height);
		}
		return target;
	}

	static void close() {
		if (front != null) {
			front.destroyBuffers();
			front = null;
		}
		if (back != null) {
			back.destroyBuffers();
			back = null;
		}
	}
}
