package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.LightTexture;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector4f;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where the shader pack puts the scene in its buffers. Packs that render at a lower resolution and upscale (Kappa's
 * TAAU) move every vertex in their own vertex shaders, so the scene fills only a corner of the full-size depth buffer.
 * Channels are drawn at full resolution, so the scene depth is resampled through this transform before it is compared
 * with them.
 * <p>
 * The transform is measured, since every pack does it its own way. A quad at known clip coordinates is drawn with a copy
 * of the entity pipeline, which Iris draws with the pack's own program, so the quad goes through the pack's vertex
 * shader. The copy writes no colour and the depth is put back afterwards. Where the depth changed is read back, and per
 * axis the pack's pixel is {@code scale * pixel + offset}. This covers scaling and moving, not other vertex
 * transforms. It is measured again when the pack's pipeline or the size changes.
 */
final class SceneScale {
	/** The probe quad covers this much of clip space on each side of the centre. Its edges stay inside the screen. */
	private static final float PROBE_HALF = 0.9F;
	/**
	 * The view distance of the probe quad. Only its screen position counts. Before 26.1 there is no depth test that always
	 * passes and still writes depth, so the quad is drawn with LEQUAL just beyond the near plane, in front of nearly everything.
	 */
	private static final float PROBE_DISTANCE = 0.1F;
	/** Below these the measured transform is the pack's sub-pixel jitter, not a scale. */
	private static final float IDENTITY_SCALE = 0.01F;
	private static final float IDENTITY_OFFSET = 2.0F;
	/** Defines carry the transform as integers in this fixed point. */
	private static final int FIXED = 100000;
	private static final int MAX_TRIES = 3;
	private static final ResourceLocation WHITE = ExitMod.id("textures/misc/white.png");

	private enum State { UNKNOWN, READING, KNOWN }

	private static State state = State.UNKNOWN;
	private static @Nullable Object measuredPipeline;
	private static int measuredWidth;
	private static int measuredHeight;
	/** Counts measurements, so a readback that arrives after the pack changed is dropped. */
	private static int generation;
	private static int tries;
	private static boolean identity = true;
	private static float scaleX = 1;
	private static float offsetX;
	private static float scaleY = 1;
	private static float offsetY;
	/** A transform read back during a frame. It is used from the next frame on, so one frame does not mix two. */
	private static float @Nullable [] measured;

	private static @Nullable BufferBuilder probeBuilder;
	private static @Nullable TextureTarget beforeProbe;
	private static @Nullable TextureTarget afterProbe;
	private static @Nullable TextureTarget probeMask;
	private static Gpu.@Nullable Pipeline maskPipeline;
	private static final Map<String, Gpu.Pipeline> RESAMPLE = new HashMap<>();

	private SceneScale() {}

	/** Whether the scene depth has to be resampled to line up with the channels. */
	static boolean scaled() {
		return !identity;
	}

	/**
	 * Measures the transform if it is not known for the current pack and size. Called while the pack renders the level,
	 * with no render pass open.
	 */
	static void measureIfNeeded() {
		RenderTarget main = Gpu.mainTarget();
		Object pipeline = IrisCompat.pipeline();
		if (pipeline != measuredPipeline || main.width != measuredWidth || main.height != measuredHeight) {
			reset(pipeline, main.width, main.height);
		}
		// With a resolution scaling mod the scene and the output differ in size, which the merge already handles.
		if (state != State.UNKNOWN || pipeline == null || SceneSize.mergeStretched()) {
			return;
		}
		Camera camera = ExitRuntime.camera();
		if (camera == null) {
			return;
		}
		state = State.READING;
		try {
			probe(main, camera);
		} catch (RuntimeException e) {
			ExitRuntime.warnOnce("scene_scale_probe", "EXIT: could not measure the shader pack's render scale: " + e);
			state = State.KNOWN;
		}
	}

	private static void reset(@Nullable Object pipeline, int width, int height) {
		measuredPipeline = pipeline;
		measuredWidth = width;
		measuredHeight = height;
		generation++;
		tries = 0;
		state = State.UNKNOWN;
		measured = null;
		setIdentity();
	}

	private static void setIdentity() {
		identity = true;
		scaleX = 1;
		offsetX = 0;
		scaleY = 1;
		offsetY = 0;
	}

	/** Draws the probe quad through the pack, finds the pixels it changed and reads them back. */
	private static void probe(RenderTarget main, Camera camera) {
		beforeProbe = ensure(beforeProbe, "emergency_exit_scale_before", main.width, main.height, false);
		afterProbe = ensure(afterProbe, "emergency_exit_scale_after", main.width, main.height, false);
		probeMask = ensure(probeMask, "emergency_exit_scale_mask", main.width, main.height, true);

		// Iris 1.2 and older draw into a depth texture of their own; bring it into the main depth first.
		SceneDepth.syncMainDepth();
		Gpu.copyDepth(beforeProbe, main);
		boolean drawn = drawProbe(main, camera);
		if (!drawn) {
			ExitRuntime.warnOnce("scene_scale_camera", "EXIT: could not place the render scale probe; occlusion assumes full resolution");
			state = State.KNOWN;
			return;
		}
		SceneDepth.syncMainDepth();
		Gpu.copyDepth(afterProbe, main);
		// The pack never sees the quad.
		SceneDepth.restoreMainDepth(beforeProbe);

		if (maskPipeline == null) {
			maskPipeline = Gpu.colorPipeline("pipeline/scale_probe", "core/scale_probe", false, Map.of(), "Before", "After");
		}
		boolean bypass = IrisCompat.bypass();
		IrisCompat.setBypass(true);
		try {
			// On this version a pass always covers the whole output.
			Gpu.draw(maskPipeline, probeMask, Gpu.depth("Before", beforeProbe), Gpu.depth("After", afterProbe));
		} finally {
			IrisCompat.setBypass(bypass);
		}
		readBack(probeMask, generation);
	}

	/** Draws the quad at {@link #PROBE_HALF} of clip space with the pack's entity program. False if it cannot. */
	private static boolean drawProbe(RenderTarget main, Camera camera) {
		Matrix4f modelView = RenderSystem.getModelViewMatrix().copy();
		Matrix4f toModel = modelView.copy();
		Matrix4f fromClip = ExitRuntime.projectionMatrix().copy();
		if (!toModel.invert() || !fromClip.invert()) {
			return false;
		}
		float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
		Vector4f[] points = new Vector4f[corners.length];
		for (int i = 0; i < corners.length; i++) {
			// A point on the ray through the corner, moved to a fixed distance in front of the camera.
			Vector4f point = new Vector4f(corners[i][0] * PROBE_HALF, corners[i][1] * PROBE_HALF, 0.5F, 1);
			point.transform(fromClip);
			point.perspectiveDivide();
			if (!(point.z() < 0)) {
				return false;
			}
			float toDistance = PROBE_DISTANCE / -point.z();
			point.set(point.x() * toDistance, point.y() * toDistance, point.z() * toDistance, 1);
			point.transform(toModel);
			points[i] = point;
		}

		RenderType type = RenderType.entitySolid(WHITE);
		if (probeBuilder == null) {
			// A BufferBuilder mallocs its store and cannot free it on this version, so one is kept for the probe.
			probeBuilder = new BufferBuilder(1024);
		}
		BufferBuilder builder = probeBuilder;
		// Built in the format Iris gives the entity program while the level renders, so the pack's program reads it.
		builder.begin(VertexFormat.Mode.QUADS, type.format());
		for (Vector4f point : points) {
			builder.vertex(point.x(), point.y(), point.z(), 1, 1, 1, 1, 0, 0, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0, 0, 1);
		}
		builder.end();
		draw(type, builder, modelView);
		return true;
	}

	/**
	 * Draws the quad with the render type, which Iris draws with the pack's program into the pack's buffers, writing
	 * depth and no colour. There is no pipeline to copy on this version: the masks are set after the render type's
	 * state, as FeatureDrawer does for depth only.
	 */
	private static void draw(RenderType type, BufferBuilder builder, Matrix4f modelView) {
		PoseStack modelViewStack = RenderSystem.getModelViewStack();
		modelViewStack.pushPose();
		modelViewStack.last().pose().load(modelView);
		RenderSystem.applyModelViewMatrix();
		type.setupRenderState();
		try {
			GL11.glColorMask(false, false, false, false);
			GL11.glDepthMask(true);
			BufferUploader.end(builder);
		} finally {
			GL11.glColorMask(true, true, true, true);
			type.clearRenderState();
			modelViewStack.popPose();
			RenderSystem.applyModelViewMatrix();
		}
	}

	/** Reads the mask back. Before 1.21.5 there is no buffer to copy it into, so it is read at once. */
	private static void readBack(TextureTarget mask, int expected) {
		int width = mask.width;
		int height = mask.height;
		ByteBuffer data = MemoryUtil.memAlloc(width * height * 4);
		try {
			int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
			GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mask.frameBufferId);
			GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
			GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
			if (expected == generation) {
				solve(data, width, height);
			}
		} finally {
			MemoryUtil.memFree(data);
		}
	}

	/** Finds the box the quad covers and solves the transform from it. Rows start at the bottom, like gl_FragCoord. */
	private static void solve(ByteBuffer data, int width, int height) {
		int minX = width;
		int maxX = -1;
		int minY = height;
		int maxY = -1;
		for (int y = 0; y < height; y++) {
			int row = y * width * 4;
			for (int x = 0; x < width; x++) {
				if (data.get(row + x * 4) != 0) {
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
			}
		}
		if (maxX < 0) {
			// Nothing changed: the pack did not draw the quad. Try a few more frames, then keep the depth as it is.
			state = ++tries < MAX_TRIES ? State.UNKNOWN : State.KNOWN;
			if (state == State.KNOWN) {
				ExitRuntime.warnOnce("scene_scale_empty", "EXIT: the shader pack drew nothing for the render scale probe; "
					+ "occlusion assumes full resolution");
			}
			return;
		}
		// A pixel is covered when its centre is inside, so the edges lie within half a pixel of these.
		float[] x = axis(minX, maxX + 1, width);
		float[] y = axis(minY, maxY + 1, height);
		measured = new float[] {x[0], x[1], y[0], y[1]};
		state = State.KNOWN;
	}

	/** Starts using a transform that was read back during this frame. */
	static void endFrame() {
		float[] next = measured;
		if (next == null) {
			return;
		}
		measured = null;
		scaleX = next[0];
		offsetX = next[1];
		scaleY = next[2];
		offsetY = next[3];
		identity = Math.abs(scaleX - 1) < IDENTITY_SCALE && Math.abs(scaleY - 1) < IDENTITY_SCALE
			&& Math.abs(offsetX) < IDENTITY_OFFSET && Math.abs(offsetY) < IDENTITY_OFFSET;
		ExitMod.LOGGER.info("EXIT: shader pack render transform x {} * px + {}, y {} * px + {}{}", scaleX, offsetX, scaleY, offsetY,
			identity ? " (full resolution)" : "");
		if (identity) {
			setIdentity();
		}
	}

	/** The scale and offset of one axis from where the quad's edges should be and where they are, in pixels. */
	private static float[] axis(int low, int high, int size) {
		float expectedLow = (1 - PROBE_HALF) / 2 * size;
		float expectedHigh = (1 + PROBE_HALF) / 2 * size;
		float scale = (high - low) / (expectedHigh - expectedLow);
		return new float[] {scale, low - scale * expectedLow};
	}

	/** Writes the depth of {@code source}, as the pack placed it, into {@code target} at full resolution. */
	static void resample(RenderTarget target, RenderTarget source) {
		Map<String, Integer> defines = new LinkedHashMap<>();
		defines.put("SCALE_X", Math.round(scaleX * FIXED));
		defines.put("OFFSET_X", Math.round(offsetX * FIXED));
		defines.put("SCALE_Y", Math.round(scaleY * FIXED));
		defines.put("OFFSET_Y", Math.round(offsetY * FIXED));
		defines.put("FIXED", FIXED);
		String key = defines.values().toString();
		Gpu.Pipeline pipeline = RESAMPLE.computeIfAbsent(key, k -> Gpu.depthReplacePipeline(
			"pipeline/depth_resample_" + RESAMPLE.size(), "core/depth_resample", defines, "Source"));
		// It runs while the pack renders the level, so keep Iris from looking for a program of the pack for it.
		boolean bypass = IrisCompat.bypass();
		IrisCompat.setBypass(true);
		try {
			Gpu.draw(pipeline, target, Gpu.depth("Source", source));
		} finally {
			IrisCompat.setBypass(bypass);
		}
	}

	private static TextureTarget ensure(@Nullable TextureTarget target, String label, int width, int height, boolean color) {
		if (target == null) {
			return color ? Gpu.colorTarget(label, width, height) : Gpu.depthTarget(label, width, height);
		}
		if (target.width != width || target.height != height) {
			Gpu.resize(target, width, height);
		}
		return target;
	}

	static void close() {
		for (TextureTarget target : new TextureTarget[] {beforeProbe, afterProbe, probeMask}) {
			if (target != null) {
				target.destroyBuffers();
			}
		}
		beforeProbe = null;
		afterProbe = null;
		probeMask = null;
		reset(null, 0, 0);
	}
}
