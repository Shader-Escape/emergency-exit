package ml.mypals.exit.smoke;

import ml.mypals.exit.api.Exit;
import ml.mypals.exit.compat.IrisCompat;
import ml.mypals.exit.impl.DebugShapes;
import ml.mypals.exit.impl.DebugView;
import ml.mypals.exit.impl.ExitRuntime;
import ml.mypals.exit.impl.SceneSize;
import ml.mypals.exit.smoke.mixin.CreateWorldScreenAccessor;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.config.IrisConfig;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Development check, not part of the mod. It creates a new creative world and turns on the test shapes. Then it runs
 * one phase per shader pack setting: without shaders, then with each pack in the {@code shaderpacks} folder (or the
 * comma-separated list in {@code -Demergency_exit.smoke.packs}, where {@code none} means shaders off). Each phase
 * switches the pack through Iris, takes screenshots and checks the counters, then the game quits. Each loader has a
 * small entry point that calls {@link #init} and {@link #tick}. Run it with {@code ./gradlew :fabric:runSmokeClient} or
 * {@code ./gradlew :neoforge:runSmokeClient}. The game exits with code 2 when it did not reach the world in time, and
 * with code 3 when a check failed.
 */
public final class SmokeTest {
	private static final String TAG = "[emergency_exit-smoke] ";
	private static final String WORLD = "EXIT Smoke";
	private static final String VERSION = SharedConstants.getCurrentVersion().name();
	private static final String NO_PACK = "none";
	/** Client ticks of one phase, 20 per second. */
	private static final int PHASE_TICKS = 220;
	/** Ticks after switching the pack before the counters of a phase start, while the pack loads. */
	private static final int SETTLE_TICKS = 60;
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

	private final List<String> phases = new ArrayList<>();
	private final List<String> failedPhases = new ArrayList<>();
	private int phase;
	/** Where the player stood when the shapes were placed, as a teleport target. */
	private String frontView = "";
	/** The counters when the current phase started counting. */
	private long[] phaseStart = new long[0];
	private boolean irisConfigSaved;
	private boolean savedShadersEnabled;
	private String savedPack = "";

	/** Called when the client initializes. */
	public void init(String loader) {
		this.loader = loader;
		deleteOldWorld();
	}

	/**
	 * Called inside Iris's shadow pass (ShadowRendererMixin; Iris for 1.21.8 has no shadow render callback). EXIT
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
			WorldCreationUiState state = screen.getUiState();
			state.setName(WORLD);
			state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
			log("creating the world");
			this.created = true;
			((CreateWorldScreenAccessor) screen).emergency_exit$onCreate();
			return;
		}
		// Wait for the title screen to settle, then open the create world screen.
		if (!this.requestedScreen && mc.getOverlay() == null && this.ticks > 40) {
			this.requestedScreen = true;
			log("opening the create-world screen");
			CreateWorldScreen.openFresh(mc, mc.screen);
		}
	}

	/**
	 * Lifts the player into open sky, places the boxes in front of it, then runs the phases: each takes screenshots of
	 * the normal view, some debug views and a view from the side.
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
			this.frontView = String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.3f %.3f", mc.player.getX(), mc.player.getY(), mc.player.getZ(),
				mc.player.getYRot(), mc.player.getXRot());
			this.phases.addAll(phases(mc));
			log("placed the test shapes at " + DebugShapes.center() + "; phases: " + this.phases);
		}
		if (this.placedAt < 0) {
			return;
		}
		int inPhase = this.ticksInWorld - this.placedAt - this.phase * PHASE_TICKS;
		String name = this.phases.get(this.phase);
		switch (inPhase) {
			case 1 -> this.startPhase(mc, name);
			case SETTLE_TICKS -> this.phaseStart = counters();
			case 100 -> {
				log("scene " + SceneSize.width() + "x" + SceneSize.height() + ", window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
					+ ", main target " + mc.getMainRenderTarget().width + "x" + mc.getMainRenderTarget().height
					+ ", merged stretched: " + SceneSize.mergeStretched());
				this.screenshot(mc, name, "normal");
			}
			case 102 -> this.presented(mc, name, "presented");
			case 103 -> DebugView.set(DebugView.Mode.COLOR, Exit.MAIN);
			case 115 -> this.screenshot(mc, name, "color");
			case 116 -> DebugView.set(DebugView.Mode.TARGET_DEPTH, Exit.MAIN);
			case 130 -> this.screenshot(mc, name, "target_depth");
			case 131 -> DebugView.set(DebugView.Mode.SCENE_DEPTH, Exit.MAIN);
			case 145 -> this.screenshot(mc, name, "scene_depth");
			case 146 -> DebugView.set(DebugView.Mode.OFF, Exit.MAIN);
			case 150 -> viewFromTheSide(mc);
			case 200 -> this.presented(mc, name, "side");
			case PHASE_TICKS -> {
				this.endPhase(name);
				this.phase++;
				if (this.phase >= this.phases.size()) {
					this.finish(mc);
				}
			}
			default -> {
			}
		}
	}

	/** The phases: no shaders, then each shader pack. Without Iris only the first. */
	private static List<String> phases(Minecraft mc) {
		String listed = System.getProperty("emergency_exit.smoke.packs");
		if (listed != null && !listed.isBlank()) {
			return Arrays.stream(listed.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
		}
		List<String> phases = new ArrayList<>();
		phases.add(NO_PACK);
		if (!IrisCompat.LOADED) {
			return phases;
		}
		Path packs = mc.gameDirectory.toPath().resolve("shaderpacks");
		if (Files.isDirectory(packs)) {
			try (Stream<Path> files = Files.list(packs)) {
				files.filter(p -> Files.isDirectory(p) || p.getFileName().toString().endsWith(".zip"))
					.map(p -> p.getFileName().toString()).sorted().forEach(phases::add);
			} catch (IOException e) {
				log("could not list the shader packs: " + e);
			}
		}
		return phases;
	}

	private void startPhase(Minecraft mc, String pack) {
		log("phase " + (this.phase + 1) + "/" + this.phases.size() + ": " + pack);
		if (this.phase > 0) {
			mc.player.connection.sendCommand(this.frontView);
		}
		if (IrisCompat.LOADED) {
			if (!this.irisConfigSaved) {
				this.irisConfigSaved = true;
				this.savedShadersEnabled = IrisPacks.shadersEnabled();
				this.savedPack = IrisPacks.packName();
			}
			IrisPacks.use(pack);
		} else if (!NO_PACK.equals(pack)) {
			log("Iris is not loaded, so pack " + pack + " cannot be used");
		}
	}

	/** Checks what EXIT did in this phase, from the counters since the pack settled. */
	private void endPhase(String pack) {
		long[] now = counters();
		long[] d = new long[now.length];
		for (int i = 0; i < now.length; i++) {
			d[i] = now[i] - this.phaseStart[i];
		}
		boolean wantPack = !NO_PACK.equals(pack);
		boolean packInUse = IrisCompat.isPackInUse();
		log("phase " + pack + ": shader pack in use: " + packInUse + ", frames composited by EXIT: " + d[0]
			+ ", drawings: " + d[1] + " (routed: " + d[2] + "), batches: " + d[3] + ", portals: " + d[4]
			+ " (passes moved into the channel: " + d[5] + "), portal/routing mismatches: " + d[6]
			+ ", frames blending channels by depth: " + d[7] + ", Iris shadow callbacks: " + d[8]);
		boolean ok = packInUse == wantPack && d[1] > 0 && d[3] > 0 && d[4] > 0 && d[6] == 0
			&& (packInUse ? d[0] > 0 && d[2] > 0 && d[5] > 0 && d[7] > 0 && d[8] > 0 : d[0] == 0 && d[2] == 0 && d[5] == 0);
		log("phase " + pack + ": " + (ok ? "OK" : "FAILED"));
		if (!ok) {
			this.failedPhases.add(pack);
		}
	}

	private void finish(Minecraft mc) {
		this.done = true;
		if (this.irisConfigSaved) {
			// Leave the Iris settings as the run found them.
			IrisPacks.restore(this.savedShadersEnabled, this.savedPack);
		}
		DebugShapes.Stats stats = DebugShapes.stats();
		log("redirects outside a portal that did not throw: " + stats.unguardedRedirects + ", off-thread portal ok: " + stats.offThreadOk);
		log("shadow pass: Iris shadow callbacks: " + shadowCallbacks + ", EXIT drawings and portals run in them: " + shadowPassRuns);
		boolean ok = this.failedPhases.isEmpty() && stats.unguardedRedirects == 0 && Boolean.TRUE.equals(stats.offThreadOk) && shadowPassRuns == 0;
		log(ok ? "RESULT: OK (phases " + this.phases + " rendered through EXIT)"
			: "RESULT: FAILED (failed phases: " + this.failedPhases + ")");
		if (!ok) {
			Runtime.getRuntime().halt(3);
		}
		mc.stop();
	}

	private static long[] counters() {
		DebugShapes.Stats stats = DebugShapes.stats();
		return new long[] {ExitRuntime.compositedFrames(), stats.draws, stats.routedDraws, stats.batchDraws, stats.portalDraws,
			stats.portalRedirectedPasses, stats.portalRoutingMismatches, ExitRuntime.blendedFrames(), shadowCallbacks};
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

	/** The screenshot name: version, loader, pack (without {@code .zip}) and view. */
	private String fileName(String pack, String view) {
		String packName = pack.endsWith(".zip") ? pack.substring(0, pack.length() - 4) : pack;
		return "emergency_exit-smoke-" + VERSION + "-" + this.loader + "-" + packName.replaceAll("[^A-Za-z0-9._+-]", "_") + "-" + view + ".png";
	}

	private void screenshot(Minecraft mc, String pack, String view) {
		String name = this.fileName(pack, view);
		Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, message -> log("screenshot " + name));
	}

	/**
	 * Saves the window's front buffer, which is what the player sees. With a resolution scaling mod the main target is
	 * not the presented frame.
	 */
	private void presented(Minecraft mc, String pack, String view) {
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
		String name = this.fileName(pack, view);
		try {
			Path screenshots = mc.gameDirectory.toPath().resolve("screenshots");
			Files.createDirectories(screenshots);
			ImageIO.write(image, "png", screenshots.resolve(name).toFile());
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

	/** Switches Iris's shader pack. Only touched when Iris is loaded. */
	private static final class IrisPacks {
		static boolean shadersEnabled() {
			return Iris.getIrisConfig().areShadersEnabled();
		}

		static String packName() {
			String name = Iris.getCurrentPackName();
			return name == null ? "" : name;
		}

		/** Turns shaders off for {@code none}, otherwise loads that pack. Iris reloads its config from disk, so save it. */
		static void use(String pack) {
			IrisConfig config = Iris.getIrisConfig();
			boolean on = !NO_PACK.equals(pack);
			config.setShadersEnabled(on);
			if (on) {
				config.setShaderPackName(pack);
			}
			try {
				config.save();
				Iris.reload();
			} catch (IOException e) {
				log("could not switch the shader pack to " + pack + ": " + e);
			}
		}

		static void restore(boolean enabled, String pack) {
			IrisConfig config = Iris.getIrisConfig();
			config.setShadersEnabled(enabled);
			if (!pack.isEmpty() && !"(off)".equals(pack)) {
				config.setShaderPackName(pack);
			}
			try {
				config.save();
			} catch (IOException e) {
				log("could not restore the Iris settings: " + e);
			}
		}
	}
}
