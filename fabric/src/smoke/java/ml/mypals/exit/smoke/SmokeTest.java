package ml.mypals.exit.smoke;

import ml.mypals.exit.api.Exit;
import ml.mypals.exit.compat.IrisCompat;
import ml.mypals.exit.impl.DebugShapes;
import ml.mypals.exit.impl.DebugView;
import ml.mypals.exit.impl.ExitRuntime;
import ml.mypals.exit.impl.SceneSize;
import ml.mypals.exit.smoke.mixin.CreateWorldScreenAccessor;
import net.coderbot.iris.Iris;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
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
	/**
	 * Client ticks to stay in the world, 20 per second. The last scene runs at 393 ticks after the shapes are placed,
	 * and the shapes are placed about 21 ticks into the world, so the run has to last a little longer than 400.
	 */
	private static final int TICKS_IN_WORLD = 420;
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
	/** The block coordinates of the water and stone walls the scenes place in front of the boxes, while they are up. */
	private int[] waterWall;
	private int[] stoneWall;
	/** The window size before the resized scene changes it, so the scene can put it back. */
	private int windowWidth;
	private int windowHeight;
	/** Whether the shader pack was reloaded, which only the run with a pack does. */
	private boolean packReloaded;

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
			// 1.19.3 is before WorldCreationUiState: the screen keeps the world name and game mode itself. Its game
			// mode type is package-private, so press its button instead; that also turns cheats on, which the tp below
			// needs.
			CreateWorldScreenAccessor legacy = (CreateWorldScreenAccessor) screen;
			legacy.emergency_exit$nameEdit().setValue(WORLD);
			CycleButton<?> mode = legacy.emergency_exit$modeButton();
			for (int i = 0; i < 4 && !"CREATIVE".equals(String.valueOf(mode.getValue())); i++) {
				mode.onPress();
			}
			log("creating the world");
			this.created = true;
			legacy.emergency_exit$onCreate();
			return;
		}
		// Wait for the title screen to settle, then open the create world screen.
		if (!this.requestedScreen && mc.getOverlay() == null && this.ticks > 40) {
			this.requestedScreen = true;
			log("opening the create-world screen");
			CreateWorldScreen.openFresh(mc, null);
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
			log("placed the test shapes at " + DebugShapes.center() + " on tick " + this.ticksInWorld);
		}
		if (this.placedAt < 0) {
			return;
		}
		// The command feedback and the teleport messages of the scenes would cover the middle of every screenshot.
		mc.gui.getChat().clearMessages(true);
		// Toasts (tutorial steps, unlocked recipes) sit in the top right corner of the shots otherwise. Clearing them
		// every tick and not only in screenshot() catches the ones raised while the frame is being drawn.
		mc.getToasts().clear();
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
			// The checks of porting.md 7.3 that can be made into a screenshot. Everything after 200 runs from the
			// side view, so the first scene levels the view and places the boxes again, the right way round.
			case 205 -> {
				DebugView.set(DebugView.Mode.MAIN_DEPTH, Exit.MAIN);
				log("scene main_depth: debug view " + DebugView.Mode.MAIN_DEPTH.id());
			}
			case 220 -> screenshot(mc, "main_depth");
			case 221 -> {
				DebugView.set(DebugView.Mode.OFF, Exit.MAIN);
				log("scene debug_off: debug view " + DebugView.Mode.OFF.id());
			}
			case 236 -> screenshot(mc, "debug_off");
			case 253 -> log("scene shapes_off: shapes " + (DebugShapes.toggle(mc.player) ? "on" : "off"));
			case 268 -> screenshot(mc, "shapes_off");
			case 269 -> {
				// Level the view, so the boxes, the water and the wall line up with the world axes.
				mc.player.connection.sendCommand("tp @s ~ ~ ~ 0 10");
				log("scene water: levelled the view");
			}
			case 270 -> log("scene water: shapes " + (DebugShapes.toggle(mc.player) ? "on" : "off") + " at " + DebugShapes.center());
			// Water flows, so it is placed only a few ticks before the screenshot; placed earlier it has spread over
			// the whole view and the boxes it is meant to cover cannot be told apart from the rest.
			case 293 -> this.waterWall = fillBetween(mc, 1, -0.5, 0.5, 0, "minecraft:water", "water");
			// The translucent-only depth is only worth a screenshot where there are translucent surfaces, so it is
			// taken here, with the water up, instead of earlier from the side view, which holds none and came out blank.
			case 294 -> {
				DebugView.set(DebugView.Mode.TRANSLUCENT_DEPTH, Exit.MAIN);
				log("scene translucent_depth: the water is up, debug view " + DebugView.Mode.TRANSLUCENT_DEPTH.id());
			}
			case 298 -> screenshot(mc, "translucent_depth");
			case 299 -> {
				DebugView.set(DebugView.Mode.OFF, Exit.MAIN);
				log("scene water: debug view back to " + DebugView.Mode.OFF.id());
			}
			case 300 -> screenshot(mc, "water");
			case 301 -> clearBetween(mc, this.waterWall, "water");
			case 302 -> this.stoneWall = fillBetween(mc, 1, -3.0, 3.0, 1, "minecraft:stone", "wall");
			case 305 -> screenshot(mc, "wall");
			case 306 -> clearBetween(mc, this.stoneWall, "wall");
			case 307 -> {
				this.windowWidth = mc.getWindow().getWidth();
				this.windowHeight = mc.getWindow().getHeight();
				GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 640, 360);
				log("scene resized: window " + this.windowWidth + "x" + this.windowHeight + " to 640x360");
			}
			case 327 -> presented(mc, "resized");
			case 328 -> {
				GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), this.windowWidth, this.windowHeight);
				log("scene resized: window back to " + this.windowWidth + "x" + this.windowHeight);
			}
			case 329 -> reloadShaderPack();
			case 348 -> {
				if (this.packReloaded) {
					screenshot(mc, "pack_reloaded");
				}
			}
			// The hand scene comes last: the held block then sits in the corner of this shot only, not of every shot
			// taken after it. It cannot be put away again reliably (the server owns the inventory), so it is simply
			// the last thing the suite does.
			// An empty hand still draws the arm over the bottom right corner, and unlike a given item it brings no
			// hotbar name label and no "new recipes" toast with it: the server owns the inventory, so a given stack is
			// re-sent every tick and the label never fades.
			case 340 -> {
				lookAt(mc, DebugShapes.center().subtract(rightOf(mc).scale(2)).add(0, 1.1, 0));
				log("scene hand: looking past the boxes, the arm over the bottom right corner");
			}
			case 395 -> screenshot(mc, "hand");
			default -> {
			}
		}
	}

	/**
	 * Fills a wall of one block in front of the boxes and returns its block coordinates, or null when it could not be
	 * placed. The wall is {@code distance} blocks in front of the middle box, runs from {@code fromRight} to
	 * {@code toRight} blocks along the player's right and {@code halfHeight} blocks up and down. The wall scene covers
	 * every box on purpose: as the blue channel is drawn without a depth test, a wall over the blue box alone looks the
	 * same as a blue box standing in front of a wall. With all of them behind it, only the blue one is left on screen.
	 */
	@Nullable
	private int[] fillBetween(Minecraft mc, double distance, double fromRight, double toRight, double halfHeight, String block, String scene) {
		Vec3 center = DebugShapes.center();
		if (center == null) {
			log("scene " + scene + ": the shapes are off, nothing placed");
			return null;
		}
		Vec3 forward = horizontalLook(mc);
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		Vec3 at = center.subtract(forward.scale(distance));
		Vec3 left = at.add(right.scale(fromRight));
		Vec3 end = at.add(right.scale(toRight));
		int x1 = (int) Math.floor(Math.min(left.x, end.x));
		int x2 = (int) Math.floor(Math.max(left.x, end.x));
		int z1 = (int) Math.floor(Math.min(left.z, end.z));
		int z2 = (int) Math.floor(Math.max(left.z, end.z));
		int y1 = (int) Math.floor(at.y - halfHeight);
		int y2 = (int) Math.floor(at.y + halfHeight);
		mc.player.connection.sendCommand("fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block);
		log("scene " + scene + ": " + block + " wall " + (x2 - x1 + 1) + " wide, " + (y2 - y1 + 1) + " tall, "
			+ (z2 - z1 + 1) + " deep at " + x1 + " " + y1 + " " + z1 + " to " + x2 + " " + y2 + " " + z2 + ", " + distance
			+ " blocks in front of the boxes, covering the boxes " + fromRight + " to " + toRight + " blocks to their right");
		return new int[] {x1, y1, z1, x2, y2, z2};
	}

	/** Removes a wall placed by {@link #fillBetween}. */
	private void clearBetween(Minecraft mc, @Nullable int[] wall, String scene) {
		if (wall == null) {
			return;
		}
		mc.player.connection.sendCommand("fill " + wall[0] + " " + wall[1] + " " + wall[2] + " " + wall[3] + " " + wall[4] + " " + wall[5] + " minecraft:air");
		log("scene " + scene + ": cleared the wall");
	}

	/** Points the player at {@code target} without moving it. */
	private void lookAt(Minecraft mc, Vec3 target) {
		mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f facing %.3f %.3f %.3f",
			mc.player.getX(), mc.player.getY(), mc.player.getZ(), target.x, target.y, target.z));
	}

	/** The player's view direction with the vertical part dropped, the way {@link DebugShapes} lines the boxes up. */
	private static Vec3 horizontalLook(Minecraft mc) {
		Vec3 look = mc.player.getLookAngle();
		return new Vec3(look.x, 0, look.z).normalize();
	}

	/** The player's horizontal right. */
	private static Vec3 rightOf(Minecraft mc) {
		Vec3 forward = horizontalLook(mc);
		return new Vec3(-forward.z, 0, forward.x);
	}

	/** Reloads the shader pack, as in porting.md 7.3 item 7. Without one there is nothing to reload. */
	private void reloadShaderPack() {
		if (!IrisCompat.isPackInUse()) {
			log("scene pack_reloaded: no shader pack in use, nothing to reload");
			return;
		}
		try {
			Iris.reload();
			this.packReloaded = true;
			log("scene pack_reloaded: reloaded the shader pack");
		} catch (IOException e) {
			log("scene pack_reloaded: the shader pack could not be reloaded: " + e);
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
		mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f facing %.3f %.3f %.3f",
			eye.x, eye.y - mc.player.getEyeHeight(), eye.z, center.x, center.y, center.z));
	}

	private void screenshot(Minecraft mc, String view) {
		// The tutorial and recipe toasts sit in the top right corner of every shot otherwise.
		mc.getToasts().clear();
		String name = "emergency_exit-smoke-" + VERSION + "-" + loader + "-" + view + ".png";
		Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> log("screenshot " + name));
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
