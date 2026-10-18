package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import ml.mypals.exit.mixin.RenderPipelinesAccessor;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The GPU work of the mod. It creates render targets and draws full-screen passes with {@link #draw}.
 */
final class Gpu {
	/** The depth of a cleared depth buffer. */
	static final double FAR_DEPTH = 0.0;

	private Gpu() {}

	static RenderTarget mainTarget() {
		return Minecraft.getInstance().gameRenderer.mainRenderTarget();
	}

	/**
	 * Whether a render pass is open. EXIT then opens no other pass and clears no target. Each command encoder only knows
	 * its own passes, so the passes of all encoders are counted (see PortalPassMixin).
	 */
	static boolean isInRenderPass() {
		return ExitRuntime.openPasses() > 0;
	}

	/** When true, passes cover the whole output instead of the scene area. */
	static boolean wholeOutput;

	static int sceneWidth() {
		return SceneSize.width();
	}

	static int sceneHeight() {
		return SceneSize.height();
	}

	/** A colour and depth target. */
	static TextureTarget colorDepthTarget(String label, int width, int height) {
		return new TextureTarget(label, width, height, true, GpuFormat.RGBA8_UNORM);
	}

	static void resize(RenderTarget target, int width, int height) {
		target.resize(width, height);
	}

	/** A target whose depth is used. It has a colour texture too, since every target has one. */
	static TextureTarget depthTarget(String label, int width, int height) {
		return colorDepthTarget(label, width, height);
	}

	/** A target with only a colour texture. */
	static TextureTarget colorTarget(String label, int width, int height) {
		return new TextureTarget(label, width, height, GpuFormat.RGBA8_UNORM, null);
	}

	/** Clears the colour to transparent black and the depth to {@link #FAR_DEPTH}. */
	static void clear(RenderTarget target) {
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
			target.getColorTexture(), new Vector4f(0, 0, 0, 0), target.getDepthTexture(), FAR_DEPTH);
	}

	/** Copies the depth of {@code source} into {@code target}. */
	static void copyDepth(RenderTarget target, RenderTarget source) {
		target.copyDepthFrom(source);
	}

	/** A texture that a full-screen pass reads with {@code texelFetch}, either the colour or the depth of a target. */
	record Sampler(String name, RenderTarget target, boolean depth) {}

	static Sampler color(String name, RenderTarget target) {
		return new Sampler(name, target, false);
	}

	static Sampler depth(String name, RenderTarget target) {
		return new Sampler(name, target, true);
	}

	/**
	 * A full-screen pass that writes RGB into a target's colour, without depth. With {@code premultiplied} it blends
	 * premultiplied colour over the target, otherwise it replaces it.
	 */
	static Pipeline colorPipeline(String location, String fragmentShader, boolean premultiplied, Map<String, Integer> defines,
		String... samplers) {
		return new Pipeline(location, fragmentShader, false, premultiplied, defines, samplers);
	}

	/** A full-screen pass that writes only a target's depth, with the default depth test. */
	static Pipeline depthPipeline(String location, String fragmentShader, Map<String, Integer> defines, String... samplers) {
		return new Pipeline(location, fragmentShader, true, false, defines, samplers);
	}

	/** A full-screen pass that replaces a target's depth, without depth test. */
	static Pipeline depthReplacePipeline(String location, String fragmentShader, Map<String, Integer> defines, String... samplers) {
		return new Pipeline(location, fragmentShader, true, false, false, true, defines, samplers);
	}

	/** A full-screen pass that replaces a target's RGBA colour and its depth, without blending or depth test. */
	static Pipeline layerPipeline(String location, String fragmentShader, Map<String, Integer> defines, String... samplers) {
		return new Pipeline(location, fragmentShader, false, false, true, defines, samplers);
	}

	static final class Pipeline {
		private final boolean depthOnly;
		private final boolean layer;
		private final String label;
		private final RenderPipeline pipeline;

		private Pipeline(String location, String fragmentShader, boolean depthOnly, boolean premultiplied, Map<String, Integer> defines,
			String[] samplers) {
			this(location, fragmentShader, depthOnly, premultiplied, false, false, defines, samplers);
		}

		private Pipeline(String location, String fragmentShader, boolean depthOnly, boolean premultiplied, boolean layer,
			Map<String, Integer> defines, String[] samplers) {
			this(location, fragmentShader, depthOnly, premultiplied, layer, false, defines, samplers);
		}

		private Pipeline(String location, String fragmentShader, boolean depthOnly, boolean premultiplied, boolean layer,
			boolean replaceDepth, Map<String, Integer> defines, String[] samplers) {
			this.depthOnly = depthOnly;
			this.layer = layer;
			this.label = ExitMod.MOD_ID + "/" + location;
			RenderPipeline.Builder builder = screenQuad(location, fragmentShader, samplers);
			defines.forEach(builder::withShaderDefine);
			if (layer) {
				layerOutput(builder);
			} else if (depthOnly) {
				depthOnly(builder, replaceDepth);
			} else {
				colorOutput(builder, premultiplied ? Optional.of(PREMULTIPLIED_ALPHA) : Optional.empty());
			}
			this.pipeline = RenderPipelinesAccessor.emergency_exit$register(builder.build());
		}
	}

	/** Draws the pipeline over {@code output} and keeps what is already there. */
	static void draw(Pipeline pipeline, RenderTarget output, Sampler... samplers) {
		try (RenderPass pass = pipeline.layer ? openPass(pipeline.label, color(output), depth(output), output)
			: pipeline.depthOnly ? depthPass(pipeline.label, output) : colorPass(pipeline.label, output)) {
			pass.setPipeline(pipeline.pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			for (Sampler sampler : samplers) {
				bind(pass, sampler.name(), sampler.depth() ? depth(sampler.target()) : color(sampler.target()));
			}
			drawScreenQuad(pass);
		}
	}

	/** Blending for premultiplied colour. */
	private static final BlendFunction PREMULTIPLIED_ALPHA = BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA;

	/** A pipeline that covers the screen and samples the given textures. */
	private static RenderPipeline.Builder screenQuad(String location, String fragmentShader, String... samplers) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withLocation(ExitMod.id(location))
			.withVertexShader("core/screenquad")
			.withFragmentShader(ExitMod.id(fragmentShader));
		BindGroupLayout.Builder layout = BindGroupLayout.builder();
		for (String name : samplers) {
			layout.withSampler(name);
		}
		builder.withBindGroupLayout(layout.build()).withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
		return builder;
	}

	/** Makes the pipeline write RGB into the target with the given blending, without depth. */
	private static void colorOutput(RenderPipeline.Builder builder, Optional<BlendFunction> blend) {
		builder.withColorTargetState(new ColorTargetState(blend, GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR));
	}

	/** Makes the pipeline replace the target's RGBA colour and depth, without blending or depth test. */
	private static void layerOutput(RenderPipeline.Builder builder) {
		builder.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true));
	}

	/**
	 * Makes a pipeline that only writes depth, with the default depth test or, to replace the depth, none. A pipeline
	 * without colour targets still gets a default one, so the pass attaches the target's colour and the pipeline writes
	 * none of it (see {@link #depthPass}).
	 */
	private static void depthOnly(RenderPipeline.Builder builder, boolean replace) {
		builder.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE))
			.withDepthStencilState(replace ? new DepthStencilState(CompareOp.ALWAYS_PASS, true) : DepthStencilState.DEFAULT);
	}

	/** Opens a pass on the colour of {@code target} and keeps its contents. */
	private static RenderPass colorPass(String label, RenderTarget target) {
		return openPass(label, color(target), null, target);
	}

	/** Opens a pass on the depth of {@code target} and keeps its contents. Its colour is attached too, unwritten (see {@link #depthOnly}). */
	private static RenderPass depthPass(String label, RenderTarget target) {
		return openPass(label, color(target), depth(target), target);
	}

	/** Draws the full-screen shape of a {@link #screenQuad} pipeline. */
	private static void drawScreenQuad(RenderPass pass) {
		pass.draw(3, 1, 0, 0);
	}

	private static GpuTextureView color(RenderTarget target) {
		return target.getColorTextureView();
	}

	private static GpuTextureView depth(RenderTarget target) {
		return target.getDepthTextureView();
	}

	private static RenderPass openPass(String label, @Nullable GpuTextureView color, @Nullable GpuTextureView depth, RenderTarget size) {
		RenderPassDescriptor descriptor = RenderPassDescriptor.create(() -> label);
		if (color != null) {
			descriptor.withColorAttachment(color);
		}
		if (depth != null) {
			descriptor.withDepthAttachment(depth, OptionalDouble.empty());
		}
		descriptor.withRenderArea(wholeOutput
			? new RenderPass.RenderArea(0, 0, size.width, size.height)
			: new RenderPass.RenderArea(0, 0, sceneWidth(), sceneHeight()));
		return RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor);
	}

	private static void bind(RenderPass pass, String name, GpuTextureView view) {
		pass.bindTexture(name, view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
	}
}
