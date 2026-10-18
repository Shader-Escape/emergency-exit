package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A debugging aid: pictures of EXIT's buffers during one frame, saved as numbered PNGs in
 * {@code screenshots/exit-frame-dump}. Off unless {@link #arm} was called; then it records the next level frame.
 */
public final class FrameDump {
	private enum Mode { DEPTH, COLOR, DIFFERENT, NEARER, OPAQUE }

	private static final Map<Mode, Gpu.Pipeline> PIPELINES = new HashMap<>();
	private static boolean armed;
	private static boolean active;
	private static int count;

	private FrameDump() {}

	/** Records the next level frame. */
	public static void arm() {
		armed = true;
	}

	static boolean active() {
		return active;
	}

	static void beginLevel() {
		active = armed;
		armed = false;
		count = 0;
		if (active) {
			try {
				Path folder = folder();
				Files.createDirectories(folder);
				try (var old = Files.list(folder)) {
					for (Path file : old.toList()) {
						Files.delete(file);
					}
				}
			} catch (IOException e) {
				ExitMod.LOGGER.warn("EXIT: could not prepare the frame dump folder", e);
				active = false;
			}
		}
	}

	static void endLevel() {
		if (active) {
			ExitMod.LOGGER.info("EXIT: frame dump of {} pictures in {}", count, folder());
		}
		active = false;
	}

	static void depth(String name, RenderTarget source) {
		save(name, Mode.DEPTH, Gpu.depth("A", source));
	}

	/** Premultiplied colour over a checkerboard, so what is transparent shows. */
	static void color(String name, RenderTarget source) {
		save(name, Mode.COLOR, Gpu.color("A", source));
	}

	/** Colour as it is, ignoring alpha. */
	static void frame(String name, RenderTarget source) {
		save(name, Mode.OPAQUE, Gpu.color("A", source));
	}

	/** Depth {@code a}, marked where {@code b} differs. */
	static void different(String name, RenderTarget a, RenderTarget b) {
		save(name, Mode.DIFFERENT, Gpu.depth("A", a), Gpu.depth("B", b));
	}

	/** Depth {@code a}, marked where it is nearer than {@code b}. */
	static void nearer(String name, RenderTarget a, RenderTarget b) {
		save(name, Mode.NEARER, Gpu.depth("A", a), Gpu.depth("B", b));
	}

	private static void save(String name, Mode mode, Gpu.Sampler... samplers) {
		if (!active) {
			return;
		}
		RenderTarget size = samplers[0].target();
		String file = String.format(Locale.ROOT, "%02d-%s.png", ++count, name);
		TextureTarget picture = Gpu.colorTarget("emergency_exit_frame_dump", size.width, size.height);
		Gpu.Pipeline pipeline = PIPELINES.computeIfAbsent(mode, m -> Gpu.colorPipeline("pipeline/frame_dump_" + m.name().toLowerCase(Locale.ROOT),
			"core/frame_dump", false, Map.of("MODE", m.ordinal()), m == Mode.DIFFERENT || m == Mode.NEARER ? new String[] {"A", "B"} : new String[] {"A"}));
		boolean bypass = IrisCompat.bypass();
		IrisCompat.setBypass(true);
		Gpu.wholeOutput = true;
		try {
			Gpu.draw(pipeline, picture, samplers);
		} finally {
			Gpu.wholeOutput = false;
			IrisCompat.setBypass(bypass);
		}
		readBack(picture, folder().resolve(file));
	}

	private static void readBack(TextureTarget picture, Path file) {
		int width = picture.width;
		int height = picture.height;
		GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "emergency_exit frame dump",
			GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * 4);
		RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(picture.getColorTexture(), buffer, 0, () -> {
			try (GpuBufferSlice.MappedView view = buffer.slice().map(true, false)) {
				write(view.data(), width, height, file);
			} finally {
				buffer.close();
				picture.destroyBuffers();
			}
		}, 0);
	}

	/** Rows start at the bottom, like gl_FragCoord. Read like vanilla screenshots. */
	private static void write(ByteBuffer data, int width, int height, Path file) {
		try (NativeImage image = new NativeImage(width, height, false)) {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					image.setPixelABGR(x, height - 1 - y, data.getInt((y * width + x) * 4) | 0xFF000000);
				}
			}
			image.writeToFile(file);
		} catch (IOException e) {
			ExitMod.LOGGER.warn("EXIT: could not write {}", file, e);
		}
	}

	private static Path folder() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("exit-frame-dump");
	}
}
