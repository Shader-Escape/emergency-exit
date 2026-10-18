package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import ml.mypals.exit.api.Exit;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Debug tool that replaces the frame with one of the buffers used to merge a channel.
 */
public final class DebugView {
	public enum Mode {
		OFF("normal rendering"),
		/** The channel's colour on a grey background. */
		COLOR("color of the channel's target"),
		/** The channel's depth, seeded from the scene depth. */
		TARGET_DEPTH("depth of the channel's target (seeded from the scene depth)"),
		/** The scene depth captured before translucent surfaces. */
		SCENE_DEPTH("scene depth before translucents (the seed source)"),
		/** The final main depth, with translucent surfaces and the hand. */
		MAIN_DEPTH("final main depth"),
		/** Only the translucent surfaces in front of the opaque scene. */
		TRANSLUCENT_DEPTH("translucent-only depth (main depth vs. depth before translucents)");

		public final String description;

		Mode(String description) {
			this.description = description;
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	private static Mode mode = Mode.OFF;
	private static ResourceLocation channel = Exit.MAIN;
	private static final Gpu.Pipeline[] PIPELINES = new Gpu.Pipeline[Mode.values().length * 2];

	private DebugView() {}

	public static Mode mode() {
		return mode;
	}

	/** The channel the view shows. */
	public static ResourceLocation channel() {
		return channel;
	}

	public static void set(Mode newMode, ResourceLocation newChannel) {
		mode = newMode;
		channel = newChannel;
	}

	static void draw(RenderTarget main, RenderTarget target) {
		// Scaled the same way as the merge when the main target is larger than the scene.
		boolean stretched = SceneSize.mergeStretched();
		int index = mode.ordinal() * 2 + (stretched ? 1 : 0);
		Gpu.Pipeline pipeline = PIPELINES[index];
		if (pipeline == null) {
			Map<String, Integer> defines = new LinkedHashMap<>();
			defines.put("MODE", mode.ordinal());
			if (stretched) {
				defines.put("STRETCHED", 1);
			}
			pipeline = Gpu.colorPipeline("pipeline/debug_view/" + mode.id() + (stretched ? "_stretched" : ""), "core/debug_view", false, defines,
				"ExitColor", "ExitDepth", "MainDepth", "OpaqueDepth");
			PIPELINES[index] = pipeline;
		}
		RenderTarget opaque = SceneDepth.opaque();
		Gpu.wholeOutput = stretched;
		try {
			Gpu.draw(pipeline, main,
				Gpu.color("ExitColor", target),
				Gpu.depth("ExitDepth", target),
				Gpu.depth("MainDepth", stretched ? main : SceneDepth.current(main)),
				Gpu.depth("OpaqueDepth", opaque != null ? opaque : stretched ? main : SceneDepth.current(main)));
		} finally {
			Gpu.wholeOutput = false;
		}
	}
}
