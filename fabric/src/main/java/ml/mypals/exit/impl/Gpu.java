package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import ml.mypals.exit.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The GPU work of the mod. It creates render targets and draws full-screen passes with {@link #draw}. Minecraft 1.21.4
 * and older have no {@code RenderPipeline} / {@code RenderPass} API, so this is plain OpenGL: the shaders are compiled
 * here and a full-screen triangle is drawn from {@code gl_VertexID}.
 */
final class Gpu {
	/** The depth of a cleared depth buffer. Depth is standard before 26.2 (near = 0, far = 1). */
	static final double FAR_DEPTH = 1.0;

	private Gpu() {}

	static RenderTarget mainTarget() {
		// Before 1.21.6 routing redirects getMainRenderTarget() (see MinecraftMixin); read the field itself.
		return ((MinecraftAccessor) Minecraft.getInstance()).emergency_exit$mainRenderTarget();
	}

	/** No render-pass API on this version, so a pass is never open. */
	static boolean isInRenderPass() {
		return false;
	}

	/**
	 * Kept for the merge code. There is no resolution scaling mod (Super Resolution) on this branch, so the scene is the
	 * whole output: passes always cover it and this does nothing. A shader pack that renders at a lower resolution is
	 * handled by SceneScale.
	 */
	static boolean wholeOutput;

	static int sceneWidth() {
		return SceneSize.width();
	}

	static int sceneHeight() {
		return SceneSize.height();
	}

	/** A colour and depth target. */
	static RenderTarget colorDepthTarget(String label, int width, int height) {
		RenderTarget target = new RenderTarget(width, height, true, Minecraft.ON_OSX);
		target.setClearColor(0, 0, 0, 0);
		return target;
	}

	static void resize(RenderTarget target, int width, int height) {
		target.resize(width, height, Minecraft.ON_OSX);
	}

	/** A target whose depth is used; older versions always give it a colour texture too. */
	static RenderTarget depthTarget(String label, int width, int height) {
		return colorDepthTarget(label, width, height);
	}

	/** A target with only a colour texture. */
	static RenderTarget colorTarget(String label, int width, int height) {
		RenderTarget target = new RenderTarget(width, height, false, Minecraft.ON_OSX);
		target.setClearColor(0, 0, 0, 0);
		return target;
	}

	/** Clears the colour to transparent black and the depth to {@link #FAR_DEPTH}. */
	static void clear(RenderTarget target) {
		// RenderTarget.clear() leaves the default framebuffer bound; keep whatever was bound.
		int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
		int[] viewport = new int[4];
		GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
		target.clear(Minecraft.ON_OSX);
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
		GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
	}

	private static int depthTextureReadFramebuffer;

	/** Copies the depth texture {@code texture}, of {@code target}'s size, into the depth of {@code target}. */
	static void copyDepthTexture(int texture, RenderTarget target) {
		int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
		if (depthTextureReadFramebuffer == 0) {
			depthTextureReadFramebuffer = GL30.glGenFramebuffers();
		}
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, depthTextureReadFramebuffer);
		GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
		GL30.glBlitFramebuffer(0, 0, target.width, target.height, 0, 0, target.width, target.height, GL11.GL_DEPTH_BUFFER_BIT,
			GL11.GL_NEAREST);
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
	}

	private static int depthTextureDrawFramebuffer;

	/** Copies the depth of {@code source} into the depth texture {@code texture}, of {@code source}'s size. */
	static void copyDepthIntoTexture(RenderTarget source, int texture) {
		int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
		if (depthTextureDrawFramebuffer == 0) {
			depthTextureDrawFramebuffer = GL30.glGenFramebuffers();
		}
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthTextureDrawFramebuffer);
		GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
		GL30.glBlitFramebuffer(0, 0, source.width, source.height, 0, 0, source.width, source.height, GL11.GL_DEPTH_BUFFER_BIT,
			GL11.GL_NEAREST);
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
	}

	/** Copies the depth of {@code source} into {@code target}. */
	static void copyDepth(RenderTarget target, RenderTarget source) {
		// copyDepthFrom leaves the default framebuffer bound; this runs inside the shader pack's passes.
		int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
		target.copyDepthFrom(source);
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
	}

	/**
	 * Before 1.21.6 there is no output override: while routing, draws go to whatever framebuffer is bound and
	 * {@code Minecraft.getMainRenderTarget()} hands out the channel target (MinecraftMixin).
	 */
	static Runnable redirectOutput(RenderTarget target) {
		return () -> {};
	}

	/**
	 * Makes {@code target} the framebuffer that following draws go to; run the returned action to bind back what was
	 * bound.
	 */
	static Runnable bindForDrawing(RenderTarget target) {
		int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
		int[] viewport = new int[4];
		GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
		GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.frameBufferId);
		GlStateManager._viewport(0, 0, target.width, target.height);
		return () -> {
			GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
			GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
			GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
		};
	}

	/** A texture a full-screen pass samples: the colour or the depth of a target, read with {@code texelFetch}. */
	record Sampler(String name, RenderTarget target, boolean depth) {}

	static Sampler color(String name, RenderTarget target) {
		return new Sampler(name, target, false);
	}

	static Sampler depth(String name, RenderTarget target) {
		return new Sampler(name, target, true);
	}

	/** A full-screen pass writing RGB into a target's colour without depth; blends premultiplied over it if asked. */
	static Pipeline colorPipeline(String location, String fragmentShader, boolean premultiplied, Map<String, Integer> defines,
		String... samplers) {
		return new Pipeline(location, fragmentShader, false, premultiplied, false, false, defines, samplers);
	}

	/** A full-screen pass writing only a target's depth, with the default depth test. */
	static Pipeline depthPipeline(String location, String fragmentShader, Map<String, Integer> defines, String... samplers) {
		return new Pipeline(location, fragmentShader, true, false, false, false, defines, samplers);
	}

	/** A full-screen pass that replaces a target's depth, without depth test. The target is cleared first. */
	static Pipeline depthReplacePipeline(String location, String fragmentShader, Map<String, Integer> defines, String... samplers) {
		return new Pipeline(location, fragmentShader, true, false, false, true, defines, samplers);
	}

	/** A full-screen pass that replaces a target's RGBA colour and depth, without blending or depth test. */
	static Pipeline layerPipeline(String location, String fragmentShader, Map<String, Integer> defines, String... samplers) {
		return new Pipeline(location, fragmentShader, false, false, true, false, defines, samplers);
	}

	/** A compiled OpenGL program that covers the screen and samples the given textures. */
	static final class Pipeline {
		private static final String VERTEX_SHADER = """
			#version 330
			void main() {
			    vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
			    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
			}
			""";

		final boolean depthOnly;
		final boolean layer;
		final boolean replaceDepth;
		private final String label;
		private final String fragmentShader;
		private final boolean premultiplied;
		private final Map<String, Integer> defines;
		private final String[] samplers;
		private int program;
		private int[] samplerLocations;
		private boolean failed;

		private Pipeline(String location, String fragmentShader, boolean depthOnly, boolean premultiplied, boolean layer,
			boolean replaceDepth, Map<String, Integer> defines, String[] samplers) {
			this.label = ExitMod.MOD_ID + "/" + location;
			this.fragmentShader = fragmentShader;
			this.depthOnly = depthOnly;
			this.premultiplied = premultiplied;
			this.layer = layer;
			this.replaceDepth = replaceDepth;
			this.defines = defines;
			this.samplers = samplers;
		}

		private void compile() {
			String source = readShader("/assets/" + ExitMod.MOD_ID + "/shaders/" + this.fragmentShader + ".fsh");
			StringBuilder header = new StringBuilder("#define STANDARD_DEPTH\n");
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
				throw new IllegalStateException("Could not link " + this.fragmentShader + ": " + GL20.glGetProgramInfoLog(program));
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
				throw new IllegalStateException("Could not compile shader: " + GL20.glGetShaderInfoLog(shader));
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
				throw new IllegalStateException("Could not read " + path, e);
			}
		}
	}

	private static int emptyVertexArray;

	/** Draws the pipeline over {@code output} and keeps what is already there. */
	static void draw(Pipeline pipeline, RenderTarget output, Sampler... samplers) {
		if (pipeline.failed) {
			return;
		}
		if (pipeline.program == 0) {
			try {
				pipeline.compile();
			} catch (RuntimeException e) {
				// Leave the frame as it is instead of crashing the game; report it once.
				pipeline.failed = true;
				ExitMod.LOGGER.error("Could not set up {}; it will not be drawn", pipeline.label, e);
				return;
			}
		}
		if (emptyVertexArray == 0) {
			// 1.16.5 has no vertex-array handling in GlStateManager; take the GL calls directly.
			emptyVertexArray = GL30.glGenVertexArrays();
		}
		int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
		int[] viewport = new int[4];
		GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
		boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
		int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		// Minecraft caches the bound program, vertex array and textures; restore exactly what was bound, or its next
		// draw runs without its shader (depth only, no colour).
		int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
		int[] textures = new int[samplers.length];
		for (int i = 0; i < samplers.length; i++) {
			GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
			textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		}

		// A layer or a depth replacement replaces the whole target.
		if (pipeline.layer || pipeline.replaceDepth) {
			clear(output);
		}
		GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, output.frameBufferId);
		GlStateManager._viewport(0, 0, output.width, output.height);
		if (pipeline.depthOnly) {
			GlStateManager._colorMask(false, false, false, false);
			GlStateManager._depthMask(true);
			GlStateManager._enableDepthTest();
			GlStateManager._depthFunc(GL11.GL_LEQUAL);
			GlStateManager._disableBlend();
		} else if (pipeline.layer) {
			GlStateManager._colorMask(true, true, true, true);
			GlStateManager._depthMask(true);
			GlStateManager._enableDepthTest();
			GlStateManager._depthFunc(GL11.GL_LEQUAL);
			GlStateManager._disableBlend();
		} else {
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
		GlStateManager._glUseProgram(pipeline.program);
		for (int i = 0; i < samplers.length; i++) {
			GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
			GlStateManager._bindTexture(samplers[i].depth() ? samplers[i].target().getDepthTextureId() : samplers[i].target().getColorTextureId());
			GlStateManager._glUniform1i(pipeline.location(samplers[i].name()), i);
		}
		GL30.glBindVertexArray(emptyVertexArray);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

		GL30.glBindVertexArray(vertexArray);
		GlStateManager._glUseProgram(program);
		for (int i = samplers.length - 1; i >= 0; i--) {
			GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
			GlStateManager._bindTexture(textures[i]);
		}
		GlStateManager._activeTexture(activeTexture);
		GlStateManager._colorMask(true, true, true, true);
		GlStateManager._depthMask(true);
		GlStateManager._depthFunc(GL11.GL_LEQUAL);
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
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
		GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
	}
}
