package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import ml.mypals.exit.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The GPU work of the mod. It creates render targets and draws full-screen passes with {@link #draw}. Before 1.21.5
 * there is no RenderPipeline or RenderPass API: EXIT compiles its programs itself and draws with OpenGL, restoring the
 * state it touches, as LitematicaPass does.
 */
final class Gpu {
	/** The depth of a cleared depth buffer. Depth is standard before 26.2 (near = 0, far = 1). */
	static final double FAR_DEPTH = 1.0;

	private Gpu() {}

	/** The main render target itself. While EXIT routes, {@code getMainRenderTarget()} returns a channel's (MinecraftMixin). */
	static RenderTarget mainTarget() {
		return ((MinecraftAccessor) Minecraft.getInstance()).emergency_exit$mainRenderTarget();
	}

	/** Whether a render pass is open. Before 1.21.5 there are none: every draw goes to the bound framebuffer. */
	static boolean isInRenderPass() {
		return false;
	}

	/** When true, passes cover the whole output instead of the scene area. */
	static boolean wholeOutput;

	static int sceneWidth() {
		return SceneSize.width();
	}

	static int sceneHeight() {
		return SceneSize.height();
	}

	/** A colour and depth target, cleared to transparent black. */
	static TextureTarget colorDepthTarget(String label, int width, int height) {
		TextureTarget target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
		target.setClearColor(0, 0, 0, 0);
		return target;
	}

	static void resize(RenderTarget target, int width, int height) {
		// Before 1.21.2 resizing and clearing take whether to check for GL errors (macOS).
		target.resize(width, height, Minecraft.ON_OSX);
	}

	/** A target whose depth is used. It has a colour texture too, since every target has one. */
	static TextureTarget depthTarget(String label, int width, int height) {
		return colorDepthTarget(label, width, height);
	}

	/** A target with only a colour texture. */
	static TextureTarget colorTarget(String label, int width, int height) {
		TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
		target.setClearColor(0, 0, 0, 0);
		return target;
	}

	/** Clears the colour to transparent black and the depth to {@link #FAR_DEPTH}, and keeps what was bound. */
	static void clear(RenderTarget target) {
		Runnable restore = saveBinding();
		Runnable restoreDepthWrites = writeDepth();
		target.clear(Minecraft.ON_OSX);
		restoreDepthWrites.run();
		restore.run();
	}

	/** Copies the depth of {@code source} into {@code target}, and keeps what was bound. */
	static void copyDepth(RenderTarget target, RenderTarget source) {
		Runnable restore = saveBinding();
		target.copyDepthFrom(source);
		restore.run();
	}

	/** Binds {@code target} for drawing, with depth writes on. The returned runnable restores both. */
	static Runnable bindForDrawing(RenderTarget target) {
		Runnable restore = saveBinding();
		GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.frameBufferId);
		GlStateManager._viewport(0, 0, target.width, target.height);
		Runnable restoreDepthWrites = writeDepth();
		return () -> {
			restoreDepthWrites.run();
			restore.run();
		};
	}

	/** Turns depth writes on, which Iris's final pass leaves off. The returned runnable restores them. */
	private static Runnable writeDepth() {
		boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		GlStateManager._depthMask(true);
		return () -> {
			boolean paused = GlOverrides.paused;
			GlOverrides.paused = true;
			try {
				GlStateManager._depthMask(depthMask);
			} finally {
				GlOverrides.paused = paused;
			}
		};
	}

	/** Saves the bound framebuffers and the viewport. The returned runnable puts them back. */
	private static Runnable saveBinding() {
		int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
		int[] viewport = new int[4];
		GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
		return () -> {
			GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
			GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
			GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
		};
	}

	/** A texture that a full-screen pass reads with {@code texelFetch}, either the colour or the depth of a target. */
	record Sampler(String name, RenderTarget target, boolean depth) {}

	static Sampler color(String name, RenderTarget target) {
		return new Sampler(name, target, false);
	}

	static Sampler depth(String name, RenderTarget target) {
		return new Sampler(name, target, true);
	}

	private enum Output {
		/** RGB, blended as premultiplied colour or replacing, without depth. */
		COLOR,
		/** Depth only, with the default depth test. */
		DEPTH,
		/** Depth only, replacing it. */
		DEPTH_REPLACE,
		/** RGBA colour and depth, replacing both. */
		LAYER
	}

	/**
	 * A full-screen pass that writes RGB into a target's colour, without depth. With {@code premultiplied} it blends
	 * premultiplied colour over the target, otherwise it replaces it.
	 */
	static Pipeline colorPipeline(String location, String fragmentShader, boolean premultiplied, Map<String, Integer> defines,
		String... samplers) {
		return new Pipeline(location, fragmentShader, Output.COLOR, premultiplied, defines, samplers);
	}

	/** A full-screen pass that writes only a target's depth, with the default depth test. */
	static Pipeline depthPipeline(String location, String fragmentShader, Map<String, Integer> defines, String... samplers) {
		return new Pipeline(location, fragmentShader, Output.DEPTH, false, defines, samplers);
	}

	/** A full-screen pass that replaces a target's depth, without depth test. */
	static Pipeline depthReplacePipeline(String location, String fragmentShader, Map<String, Integer> defines, String... samplers) {
		return new Pipeline(location, fragmentShader, Output.DEPTH_REPLACE, false, defines, samplers);
	}

	/** A full-screen pass that replaces a target's RGBA colour and its depth, without blending or depth test. */
	static Pipeline layerPipeline(String location, String fragmentShader, Map<String, Integer> defines, String... samplers) {
		return new Pipeline(location, fragmentShader, Output.LAYER, false, defines, samplers);
	}

	/** The pipelines whose program did not compile or link. Used by the checks. */
	private static final List<String> BROKEN = new ArrayList<>();

	static List<String> brokenPipelines() {
		return List.copyOf(BROKEN);
	}

	/** A program of EXIT's own: one full-screen triangle and a fragment shader from the mod's resources. */
	static final class Pipeline {
		private static final String VERTEX_SHADER = """
			#version 330
			void main() {
			    vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
			    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
			}
			""";

		private final String location;
		private final String fragmentShader;
		private final Output output;
		private final boolean premultiplied;
		private final Map<String, Integer> defines;
		private final String[] samplers;
		private int program;
		private int[] samplerLocations;
		private boolean failed;

		private Pipeline(String location, String fragmentShader, Output output, boolean premultiplied, Map<String, Integer> defines,
			String[] samplers) {
			this.location = ExitMod.MOD_ID + ":" + location;
			this.fragmentShader = fragmentShader;
			this.output = output;
			this.premultiplied = premultiplied;
			this.defines = Map.copyOf(defines);
			this.samplers = samplers;
		}

		private void compile() {
			String source = readShader("/assets/" + ExitMod.MOD_ID + "/shaders/" + this.fragmentShader + ".fsh");
			StringBuilder header = new StringBuilder();
			this.defines.forEach((key, value) -> header.append("#define ").append(key).append(' ').append(value).append('\n'));
			int afterVersion = source.indexOf('\n') + 1;
			source = source.substring(0, afterVersion) + header + source.substring(afterVersion);
			int vertex = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
			int fragment = compileShader(GL20.GL_FRAGMENT_SHADER, source);
			int program = GL20.glCreateProgram();
			GL20.glAttachShader(program, vertex);
			GL20.glAttachShader(program, fragment);
			GL20.glLinkProgram(program);
			GL20.glDeleteShader(vertex);
			GL20.glDeleteShader(fragment);
			if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
				throw new IllegalStateException("could not link " + this.location + ": " + GL20.glGetProgramInfoLog(program));
			}
			this.samplerLocations = new int[this.samplers.length];
			for (int i = 0; i < this.samplers.length; i++) {
				this.samplerLocations[i] = GL20.glGetUniformLocation(program, this.samplers[i]);
			}
			this.program = program;
		}

		private int location(String sampler) {
			for (int i = 0; i < this.samplers.length; i++) {
				if (this.samplers[i].equals(sampler)) {
					return this.samplerLocations[i];
				}
			}
			return -1;
		}

		private static int compileShader(int type, String source) {
			int shader = GL20.glCreateShader(type);
			GL20.glShaderSource(shader, source);
			GL20.glCompileShader(shader);
			if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
				String log = GL20.glGetShaderInfoLog(shader);
				GL20.glDeleteShader(shader);
				throw new IllegalStateException("could not compile a shader: " + log);
			}
			return shader;
		}

		private static String readShader(String path) {
			try (InputStream in = Gpu.class.getResourceAsStream(path)) {
				if (in == null) {
					throw new IllegalStateException(path + " not found");
				}
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new IllegalStateException("could not read " + path, e);
			}
		}
	}

	private static int emptyVertexArray;

	/** Draws the pipeline over {@code output} and keeps what is already there, except what it replaces. */
	static void draw(Pipeline pipeline, RenderTarget output, Sampler... samplers) {
		if (pipeline.failed) {
			return;
		}
		if (pipeline.program == 0) {
			try {
				pipeline.compile();
			} catch (RuntimeException e) {
				// Leave the frame as it is instead of crashing the game, and report it once.
				pipeline.failed = true;
				BROKEN.add(pipeline.location);
				ExitMod.LOGGER.error("EXIT: could not set up {}; it will not be drawn", pipeline.location, e);
				return;
			}
		}
		if (emptyVertexArray == 0) {
			emptyVertexArray = GlStateManager._glGenVertexArrays();
		}
		boolean paused = GlOverrides.paused;
		GlOverrides.paused = true;
		try {
			drawUnchecked(pipeline, output, samplers);
		} finally {
			GlOverrides.paused = paused;
		}
	}

	private static void drawUnchecked(Pipeline pipeline, RenderTarget output, Sampler... samplers) {
		Runnable restoreBinding = saveBinding();
		boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
		boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
		int activeTexture = GlStateManager._getActiveTexture();
		// Minecraft caches the bound program, vertex array and textures, so put back exactly what was bound, or its next
		// draw runs without its program.
		int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
		int[] textures = new int[samplers.length];
		for (int i = 0; i < samplers.length; i++) {
			GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
			textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		}

		GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, output.frameBufferId);
		// The scene area, not the whole output: with a resolution scaling mod the main target is larger than the scene.
		GlStateManager._viewport(0, 0, wholeOutput ? output.width : Math.min(sceneWidth(), output.width),
			wholeOutput ? output.height : Math.min(sceneHeight(), output.height));
		switch (pipeline.output) {
			case COLOR -> {
				GlStateManager._colorMask(true, true, true, false);
				GlStateManager._depthMask(false);
				GlStateManager._disableDepthTest();
				if (pipeline.premultiplied) {
					GlStateManager._enableBlend();
					GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
				} else {
					GlStateManager._disableBlend();
				}
			}
			case DEPTH, DEPTH_REPLACE -> {
				GlStateManager._colorMask(false, false, false, false);
				GlStateManager._depthMask(true);
				GlStateManager._enableDepthTest();
				GlStateManager._depthFunc(pipeline.output == Output.DEPTH ? GL11.GL_LEQUAL : GL11.GL_ALWAYS);
				GlStateManager._disableBlend();
			}
			case LAYER -> {
				GlStateManager._colorMask(true, true, true, true);
				GlStateManager._depthMask(true);
				GlStateManager._enableDepthTest();
				GlStateManager._depthFunc(GL11.GL_ALWAYS);
				GlStateManager._disableBlend();
			}
		}
		GlStateManager._glUseProgram(pipeline.program);
		for (int i = 0; i < samplers.length; i++) {
			GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
			RenderTarget target = samplers[i].target();
			GlStateManager._bindTexture(samplers[i].depth() ? target.getDepthTextureId() : target.getColorTextureId());
			GlStateManager._glUniform1i(pipeline.location(samplers[i].name()), i);
		}
		GlStateManager._glBindVertexArray(emptyVertexArray);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

		GlStateManager._glBindVertexArray(vertexArray);
		GlStateManager._glUseProgram(program);
		for (int i = samplers.length - 1; i >= 0; i--) {
			GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
			GlStateManager._bindTexture(textures[i]);
		}
		GlStateManager._activeTexture(activeTexture);
		GlStateManager._colorMask(true, true, true, true);
		GlStateManager._depthMask(depthMask);
		GlStateManager._depthFunc(depthFunc);
		if (depthTest) {
			GlStateManager._enableDepthTest();
		} else {
			GlStateManager._disableDepthTest();
		}
		if (blend) {
			GlStateManager._enableBlend();
		} else {
			GlStateManager._disableBlend();
		}
		restoreBinding.run();
	}
}
