package ml.mypals.exit.smoke;

import ml.mypals.exit.api.Exit;
import ml.mypals.exit.compat.IrisCompat;
import ml.mypals.exit.impl.DebugShapes;
import ml.mypals.exit.impl.DebugView;
import ml.mypals.exit.impl.ExitRuntime;
import ml.mypals.exit.impl.SceneSize;
import ml.mypals.exit.smoke.mixin.CreateWorldScreenAccessor;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Development check, not part of the mod. It creates a new creative world, turns on the test shapes, takes
 * screenshots and quits. Each loader has a small entry point that calls {@link #init} and {@link #tick}. Run it with
 * {@code ./gradlew :fabric:runSmokeClient} or {@code ./gradlew :neoforge:runSmokeClient}. The game exits with code 2
 * when it did not reach the world in time, and with code 3 when a check failed.
 */
public final class SmokeTest {
	private static final String TAG = "[emergency_exit-smoke] ";
	private static final String WORLD = "EXIT Smoke";
	private static final String VERSION = SharedConstants.getCurrentVersion().getName();
	/** Client ticks to stay in the world, 20 per second. */
	private static final int TICKS_IN_WORLD = 400;
	/** Give up after this many client ticks without a world. */
	private static final int TIMEOUT_TICKS = 20 * 60 * 5;

	/** Iris shadow callbacks, and the EXIT drawings and portals that ran in them. The latter should stay 0. */
	private static int shadowCallbacks;
	private static int shadowPassRuns;

	private int ticks;
	private int ticksInWorld;
	private int placedAt = -1;
	private boolean requestedScreen;
	private boolean created;
	private boolean done;

	/** The loader's name, part of the screenshot names. */
	private String loader = "unknown";

	/** Called when the client initializes. */
	public void init(String loader) {
		this.loader = loader;
		deleteOldWorld();
	}

	/**
	 * Called inside Iris's shadow pass (ShadowRendererMixin; Iris for 1.21.11 has no shadow render callback). EXIT
	 * must skip what is drawn there.
	 */
	public static void inShadowPass() {
		if (!DebugShapes.isOn() || !IrisCompat.isRenderingShadowPass()) {
			return;
		}
		shadowCallbacks++;
		Exit.draw(exit -> shadowPassRuns++);
		Exit.portal(() -> shadowPassRuns++);
	}

	/** Called at the end of each client tick. */
	public void tick(Minecraft mc) {
		if (this.done) {
			return;
		}
		this.ticks++;
		if (this.ticks == 1) {
			// The check runs unattended, and a pause menu would cover the screenshots.
			mc.options.pauseOnLostFocus = false;
		}
		if (mc.level != null && mc.player != null) {
			this.ticksInWorld++;
			if (this.ticksInWorld == 1) {
				log("in the world after " + this.ticks + " ticks");
			}
			this.showcase(mc);
			if (this.ticksInWorld >= TICKS_IN_WORLD) {
				this.done = true;
				log("shader pack in use: " + IrisCompat.isPackInUse() + ", frames composited by EXIT: " + ExitRuntime.compositedFrames());
				DebugShapes.Stats stats = DebugShapes.stats();
				log("drawings: " + stats.draws + " (routed: " + stats.routedDraws + "), batches: "
					+ stats.batchDraws + ", portals: " + stats.portalDraws + " (passes moved into the channel: " + stats.portalRedirectedPasses + "), portal/routing mismatches: "
					+ stats.portalRoutingMismatches + ", redirects outside a portal that did not throw: " + stats.unguardedRedirects
					+ ", off-thread portal ok: " + stats.offThreadOk);
				log("frames blending channels by depth: " + ExitRuntime.blendedFrames());
				log("shadow pass: Iris shadow callbacks: " + shadowCallbacks + ", EXIT drawings and portals run in them: " + shadowPassRuns);
				boolean drew = stats.draws > 0 && stats.batchDraws > 0 && stats.portalDraws > 0 && stats.portalRoutingMismatches == 0
					&& (stats.portalRedirectedPasses > 0 || !IrisCompat.isPackInUse())
					&& stats.unguardedRedirects == 0 && Boolean.TRUE.equals(stats.offThreadOk)
					&& (shadowCallbacks > 0 || !IrisCompat.isPackInUse()) && shadowPassRuns == 0
					&& (ExitRuntime.blendedFrames() > 0 || !IrisCompat.isPackInUse());
				log(drew ? "RESULT: OK (entered the world and rendered " + TICKS_IN_WORLD + " ticks through EXIT)"
					: "RESULT: FAILED (the EXIT drawings did not all run)");
				if (!drew) {
					Runtime.getRuntime().halt(3);
				}
				mc.stop();
			}
			return;
		}
		if (this.ticks > TIMEOUT_TICKS) {
			this.done = true;
			log("RESULT: FAILED (no world after " + TIMEOUT_TICKS + " ticks; screen: " + mc.screen + ")");
			Runtime.getRuntime().halt(2);
		}
		if (this.created) {
			return;
		}
		if (mc.screen instanceof CreateWorldScreen screen) {
			// 1.16.5 has no WorldCreationUiState: the screen keeps the name and the game mode itself. The mode is
			// behind a plain button that cycles survival -> hardcore -> creative, so press it until it reads Creative.
			CreateWorldScreenAccessor legacy = (CreateWorldScreenAccessor) screen;
			legacy.emergency_exit$nameEdit().setValue(WORLD);
			Button mode = legacy.emergency_exit$modeButton();
			for (int i = 0; i < 4 && !mode.getMessage().getString().toLowerCase(Locale.ROOT).contains("creative"); i++) {
				mode.onPress();
			}
			log("creating the world in " + mode.getMessage().getString());
			this.created = true;
			legacy.emergency_exit$onCreate();
			return;
		}
		// Wait for the title screen to settle, then open the create world screen.
		if (!this.requestedScreen && mc.getOverlay() == null && this.ticks > 40) {
			this.requestedScreen = true;
			log("opening the create-world screen");
			mc.setScreen(CreateWorldScreen.create(null));
		}
	}

	/**
	 * Lifts the player into open sky, places the boxes in front of it and takes screenshots of the normal view and
	 * some debug views.
	 */
	private void showcase(Minecraft mc) {
		if (this.ticksInWorld == 20) {
			mc.player.absMoveTo(mc.player.getX(), 200, mc.player.getZ(), 0, 10);
		}
		if (this.placedAt < 0 && this.ticksInWorld > 20 && mc.player.getY() > 190) {
			// Fly in place so the view stays on the boxes. 1.16.5 exposes the abilities object itself.
			mc.player.abilities.flying = true;
			mc.player.onUpdateAbilities();
			// -Demergency_exit.smoke.noShapes=true gives a control run without the shapes.
			if (!Boolean.getBoolean("emergency_exit.smoke.noShapes")) {
				DebugShapes.toggle(mc.player);
			}
			this.placedAt = this.ticksInWorld;
			log("placed the test shapes at " + DebugShapes.center());
		}
		if (this.placedAt < 0) {
			return;
		}
		switch (this.ticksInWorld - this.placedAt) {
			case 100 -> {
				log("scene " + SceneSize.width() + "x" + SceneSize.height() + ", window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
					+ ", main target " + mc.getMainRenderTarget().width + "x" + mc.getMainRenderTarget().height
					+ ", merged stretched: " + SceneSize.mergeStretched());
				screenshot(mc, "normal");
			}
			case 102 -> presented(mc, "presented");
			case 103 -> DebugView.set(DebugView.Mode.COLOR, Exit.MAIN);
			case 115 -> screenshot(mc, "color");
			case 116 -> DebugView.set(DebugView.Mode.TARGET_DEPTH, Exit.MAIN);
			case 130 -> screenshot(mc, "target_depth");
			case 131 -> DebugView.set(DebugView.Mode.SCENE_DEPTH, Exit.MAIN);
			case 145 -> screenshot(mc, "scene_depth");
			case 146 -> DebugView.set(DebugView.Mode.OFF, Exit.MAIN);
			case 150 -> viewFromTheSide(mc);
			case 200 -> presented(mc, "side");
			default -> {
			}
		}
	}

	/**
	 * Moves the player beside the row of boxes, so the red one is in front of the green and yellow ones and the blue
	 * one is behind them. It shows whether translucent drawings are sorted by depth with the other channels.
	 */
	private void viewFromTheSide(Minecraft mc) {
		Vec3 center = DebugShapes.center();
		if (center == null) {
			return;
		}
		Vec3 look = mc.player.getLookAngle();
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		Vec3 eye = center.add(right.scale(7)).add(look.x * -1.5, 0.6, look.z * -1.5);
		double dx = center.x - eye.x;
		double dy = center.y - (eye.y - mc.player.getEyeHeight());
		double dz = center.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
		mc.player.absMoveTo(eye.x, eye.y - mc.player.getEyeHeight(), eye.z, yaw, pitch);
	}

	private void screenshot(Minecraft mc, String view) {
		String name = "emergency_exit-smoke-" + VERSION + "-" + loader + "-" + view + ".png";
		Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget().width, mc.getMainRenderTarget().height,
			mc.getMainRenderTarget(), message -> log("screenshot " + name));
	}

	/**
	 * Saves the window's front buffer, which is what the player sees. With a resolution scaling mod the main target is
	 * not the presented frame.
	 */
	private void presented(Minecraft mc, String view) {
		int width = mc.getWindow().getWidth();
		int height = mc.getWindow().getHeight();
		int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
		int readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
		ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
		GL11.glReadBuffer(GL11.GL_FRONT);
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
		GL11.glReadBuffer(readBuffer);
		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int i = ((height - 1 - y) * width + x) * 4;
				image.setRGB(x, y, (pixels.get(i) & 0xFF) << 16 | (pixels.get(i + 1) & 0xFF) << 8 | pixels.get(i + 2) & 0xFF);
			}
		}
		String name = "emergency_exit-smoke-" + VERSION + "-" + loader + "-" + view + ".png";
		try {
			ImageIO.write(image, "png", mc.gameDirectory.toPath().resolve("screenshots").resolve(name).toFile());
			log("screenshot " + name);
		} catch (IOException e) {
			log("could not save " + name + ": " + e);
		}
	}

	private static void deleteOldWorld() {
		// The development runs start in the game directory.
		Path saves = Path.of("saves");
		if (!Files.isDirectory(saves)) {
			return;
		}
		try (Stream<Path> worlds = Files.list(saves)) {
			for (Path world : worlds.filter(p -> p.getFileName().toString().startsWith(WORLD)).toList()) {
				try (Stream<Path> files = Files.walk(world)) {
					for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
						Files.delete(file);
					}
				}
				log("deleted the old test world " + world.getFileName());
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not delete the old test world", e);
		}
	}

	private static void log(String message) {
		System.out.println(TAG + message);
		System.out.flush();
	}
}
