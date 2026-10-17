package ml.mypals.exit.smoke;

import ml.mypals.exit.api.Exit;
import ml.mypals.exit.compat.IrisCompat;
import ml.mypals.exit.impl.DebugShapes;
import ml.mypals.exit.impl.DebugView;
import ml.mypals.exit.impl.ExitRuntime;
import ml.mypals.exit.impl.SceneSize;
import ml.mypals.exit.smoke.mixin.CreateWorldScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.irisshaders.iris.api.v0.IrisApi;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

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
import java.util.stream.Stream;

/**
 * Development check, not part of the mod. It creates a new creative world, turns on the test shapes, takes
 * screenshots and quits. Run it with {@code ./gradlew :<version>:runSmokeClient}. The game exits with code 2 when it
 * did not reach the world in time, and with code 3 when a check failed.
 */
public final class SmokeTest implements ClientModInitializer {
	private static final String TAG = "[emergency_exit-smoke] ";
	private static final String WORLD = "EXIT Smoke";
	private static final String VERSION = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()
		.getMetadata().getVersion().getFriendlyString();
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

	@Override
	public void onInitializeClient() {
		deleteOldWorld();
		if (IrisCompat.LOADED) {
			// EXIT must skip what is drawn in Iris's shadow pass.
			IrisApi.getInstance().registerShadowRenderCallback((modelView, projection, x, y, z, tickDelta) -> {
				if (!DebugShapes.isOn() || !IrisCompat.isRenderingShadowPass()) {
					return;
				}
				shadowCallbacks++;
				Exit.draw(exit -> shadowPassRuns++);
				Exit.portal(() -> shadowPassRuns++);
			});
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft mc) {
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
				log("immediate drawings: " + stats.immediateDraws + " (deferred: " + stats.deferredDraws + ", routed: " + stats.routedDraws + "), batches: "
					+ stats.batchDraws + ", portals: " + stats.portalDraws + " (passes moved into the channel: " + stats.portalRedirectedPasses + "), portal/routing mismatches: "
					+ stats.portalRoutingMismatches + ", redirects outside a portal that did not throw: " + stats.unguardedRedirects
					+ ", off-thread portal ok: " + stats.offThreadOk);
				log("frames blending channels by depth: " + ExitRuntime.blendedFrames() + ", gizmos moved into channels: " + ExitRuntime.redirectedGizmos());
				log("shadow pass: Iris shadow callbacks: " + shadowCallbacks + ", EXIT drawings and portals run in them: " + shadowPassRuns);
				boolean drew = stats.immediateDraws > 0 && stats.batchDraws > 0 && stats.portalDraws > 0 && stats.portalRoutingMismatches == 0
					&& (stats.portalRedirectedPasses > 0 || !IrisCompat.isPackInUse())
					&& stats.unguardedRedirects == 0 && Boolean.TRUE.equals(stats.offThreadOk)
					&& (shadowCallbacks > 0 || !IrisCompat.isPackInUse()) && shadowPassRuns == 0
					&& (ExitRuntime.blendedFrames() > 0 || !IrisCompat.isPackInUse())
					&& (ExitRuntime.redirectedGizmos() > 0 || !IrisCompat.isPackInUse());
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
			log("RESULT: FAILED (no world after " + TIMEOUT_TICKS + " ticks; screen: " + mc.gui.screen() + ")");
			Runtime.getRuntime().halt(2);
		}
		if (this.created) {
			return;
		}
		if (mc.gui.screen() instanceof CreateWorldScreen screen) {
			WorldCreationUiState state = screen.getUiState();
			state.setName(WORLD);
			state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
			log("creating the world");
			this.created = true;
			((CreateWorldScreenAccessor) screen).emergency_exit$onCreate();
			return;
		}
		// Wait for the title screen to settle, then open the create world screen.
		if (!this.requestedScreen && mc.gui.overlay() == null && this.ticks > 40) {
			this.requestedScreen = true;
			log("opening the create-world screen");
			CreateWorldScreen.openFresh(mc, () -> {});
		}
	}

	/**
	 * Lifts the player into open sky, places the boxes in front of it and takes screenshots of the normal view and
	 * some debug views.
	 */
	private void showcase(Minecraft mc) {
		if (this.ticksInWorld == 20) {
			mc.player.connection.sendCommand("tp @s ~ 200 ~ 0 10");
		}
		if (this.placedAt < 0 && this.ticksInWorld > 20 && mc.player.getY() > 190) {
			// Fly in place so the view stays on the boxes.
			mc.player.getAbilities().flying = true;
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
					+ ", main target " + mc.gameRenderer.mainRenderTarget().width + "x" + mc.gameRenderer.mainRenderTarget().height
					+ ", merged stretched: " + SceneSize.mergeStretched());
				screenshot(mc, "normal");
			}
			case 102 -> presented(mc);
			case 103 -> DebugView.set(DebugView.Mode.COLOR, Exit.MAIN);
			case 115 -> screenshot(mc, "color");
			case 116 -> DebugView.set(DebugView.Mode.TARGET_DEPTH, Exit.MAIN);
			case 130 -> screenshot(mc, "target_depth");
			case 131 -> DebugView.set(DebugView.Mode.SCENE_DEPTH, Exit.MAIN);
			case 145 -> screenshot(mc, "scene_depth");
			case 146 -> DebugView.set(DebugView.Mode.OFF, Exit.MAIN);
			default -> {
			}
		}
	}

	private static void screenshot(Minecraft mc, String view) {
		String name = "emergency_exit-smoke-" + VERSION + "-" + view + ".png";
		Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> log("screenshot " + name));
	}

	/**
	 * Saves the window's front buffer, which is what the player sees. With a resolution scaling mod the main target is
	 * not the presented frame.
	 */
	private static void presented(Minecraft mc) {
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
		String name = "emergency_exit-smoke-" + VERSION + "-presented.png";
		try {
			ImageIO.write(image, "png", mc.gameDirectory.toPath().resolve("screenshots").resolve(name).toFile());
			log("screenshot " + name);
		} catch (IOException e) {
			log("could not save " + name + ": " + e);
		}
	}

	private static void deleteOldWorld() {
		Path saves = FabricLoader.getInstance().getGameDir().resolve("saves");
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
