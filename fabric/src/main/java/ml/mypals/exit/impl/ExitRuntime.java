package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.exit.api.Exit;
import ml.mypals.exit.api.ExitChannel;
import ml.mypals.exit.api.ExitOptions;
import ml.mypals.exit.api.PortalOverrides;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.math.Matrix4f;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The state behind {@link Exit}. It holds the channels, the camera and projection of the current frame, and the open
 * portals. Everything runs on the render thread, except {@link #plainFormat} and portals opened on other threads.
 */
public final class ExitRuntime {
	private static final Map<ResourceLocation, Channel> CHANNELS = new LinkedHashMap<>();
	/** The channels sorted by priority, then by creation. */
	private static List<Channel> mergeOrder = List.of();
	private static @Nullable Channel main;

	private static boolean inLevel;
	/** Set once the level's frame graph runs. From then on the main depth holds this frame's depth. */
	private static boolean passesRunning;
	private static @Nullable Camera camera;
	private static final Matrix4f VIEW_ROTATION = new Matrix4f();
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static float partialTick;

	/** The portals open on the render thread, innermost last. They all belong to {@link #portalChannel}. */
	private static final ArrayDeque<Saved> PORTALS = new ArrayDeque<>();
	private static @Nullable Channel portalChannel;
	/** Whether the outermost portal routes. Nested portals follow it. */
	private static boolean portalRouted;
	/** Binds the channel's framebuffer while a portal routes; run to bind back. */
	private static Runnable restoreOutput;
	/** The projection that was set when the outermost routing portal opened. */
	private static @Nullable Matrix4f savedProjection;
	/** True while the channel target is being prepared, so it can ask for the real main target (MinecraftMixin). */
	private static boolean preparing;

	/** The drawing running now, or null. */
	static @Nullable DrawContext drawing;
	private static long compositedFrames;
	private static final Set<String> WARNED = new HashSet<>();

	private ExitRuntime() {}

	public static ExitChannel main() {
		if (main == null) {
			main = (Channel) channel(Exit.MAIN, ExitOptions.DEFAULT);
		}
		return main;
	}

	public static ExitChannel channel(ResourceLocation id, ExitOptions options) {
		RenderSystem.assertThread(RenderSystem::isOnRenderThread);
		Channel existing = CHANNELS.get(id);
		if (existing != null) {
			if (!existing.options().equals(options)) {
				throw new IllegalArgumentException("EXIT channel " + id + " already exists with other options: " + existing.options());
			}
			return existing;
		}
		Channel channel = new Channel(id, options, CHANNELS.size());
		CHANNELS.put(id, channel);
		List<Channel> order = new ArrayList<>(CHANNELS.values());
		order.sort(Comparator.<Channel>comparingInt(c -> c.options().priority()).thenComparingInt(c -> c.order));
		mergeOrder = List.copyOf(order);
		return channel;
	}

	static @Nullable Channel find(ResourceLocation id) {
		return CHANNELS.get(id);
	}

	static Set<ResourceLocation> channelIds() {
		return CHANNELS.keySet();
	}

	/** Whether a shader pack is in use and its shadow pass is not running. */
	static boolean packActive() {
		return IrisCompat.isPackInUse() && !IrisCompat.isRenderingShadowPass();
	}

	static boolean inShadowPass() {
		return IrisCompat.isPackInUse() && IrisCompat.isRenderingShadowPass();
	}

	static boolean inLevel() {
		return inLevel;
	}

	static boolean passesRunning() {
		return passesRunning;
	}

	public static boolean isActive() {
		return inLevel && packActive();
	}

	public static boolean isRouting() {
		return RenderSystem.isOnRenderThread() && !PORTALS.isEmpty() && portalRouted;
	}

	/** The channel the open portals route into, or null when nothing routes. */
	static @Nullable Channel routingChannel() {
		return isRouting() ? portalChannel : null;
	}

	public static boolean inPortal() {
		return RenderSystem.isOnRenderThread() && !PORTALS.isEmpty();
	}

	/**
	 * Before 1.21.6 draws go to {@code Minecraft.getMainRenderTarget()}; while routing the MinecraftMixin makes it hand
	 * out the channel's target. Preparing the target itself must get the real one, hence {@link #preparing}.
	 */
	public static RenderTarget routeMainTarget(RenderTarget original) {
		if (!isRouting() || preparing || portalChannel == null) {
			return original;
		}
		preparing = true;
		try {
			return portalChannel.prepareForDraw();
		} finally {
			preparing = false;
		}
	}

	/** Redirects for {@code channel}, or for the channel of the open portal when it is null. */
	static RenderTarget redirect(@Nullable Channel channel, RenderTarget original) {
		if (!RenderSystem.isOnRenderThread()) {
			throw new IllegalStateException("EXIT: redirect called off the render thread (" + Thread.currentThread().getName()
				+ "); a portal there only switches the vertex format widening, it has no target to redirect to");
		}
		String who = channel == null ? "Exit.redirect" : channel.id() + ".redirect";
		if (PORTALS.isEmpty()) {
			throw new IllegalStateException("EXIT: " + who + " called outside a portal; call it inside portal(...) "
				+ "(or check Exit.isRouting() first in code that also runs outside one)");
		}
		if (channel != null && channel != portalChannel) {
			throw new IllegalStateException("EXIT: " + who + " called inside a portal of " + portalChannel.id());
		}
		return portalRouted ? portalChannel.prepareForDraw() : original;
	}

	public static RenderTarget redirect(RenderTarget original) {
		return redirect(null, original);
	}

	/** The main render target. */
	public static RenderTarget mainTarget() {
		return Gpu.mainTarget();
	}

	static Camera camera() {
		return camera;
	}

	/** The level's projection. */
	static Matrix4f projectionMatrix() {
		return PROJECTION;
	}

	static Matrix4f viewRotation() {
		return VIEW_ROTATION;
	}

	/** The partial tick the level renders with. */
	static float partialTick() {
		return partialTick;
	}

	private static long blendedFrames;

	/** Counts frames that blended two or more channels by depth. Used by the checks. */
	public static long blendedFrames() {
		return blendedFrames;
	}

	/** Counts frames that merged at least one channel. Used by the checks. */
	public static long compositedFrames() {
		return compositedFrames;
	}

	private static final PortalOverrides KEEP_ALL = new PortalOverrides(PortalOverrides.Switch.KEEP, PortalOverrides.Switch.KEEP, PortalOverrides.Switch.KEEP);

	/** The switches a portal found when it opened. They are restored when it ends, if it set them. */
	private record Saved(boolean applied, boolean bypass, boolean skipExtension, boolean renderingLevel) {}

	/**
	 * Opens a portal on {@code channel} and returns whether it routes. Null overrides mean the defaults, or no change
	 * inside another portal. The switches are only set while a shader pack is in use outside its shadow pass.
	 */
	static boolean begin(Channel channel, @Nullable PortalOverrides overrides) {
		RenderSystem.assertThread(RenderSystem::isOnRenderThread);
		if (!PORTALS.isEmpty() && portalChannel != channel) {
			throw new IllegalStateException("EXIT: a portal on " + channel.id() + " inside one on " + portalChannel.id());
		}
		if (PORTALS.isEmpty()) {
			portalChannel = channel;
			portalRouted = isActive();
		}
		if (overrides == null) {
			overrides = PORTALS.isEmpty() ? PortalOverrides.DEFAULT : KEEP_ALL;
		}
		boolean apply = packActive();
		Saved saved = new Saved(apply, IrisCompat.bypass(), IrisCompat.skipExtension(), IrisCompat.isRenderingLevel());
		PORTALS.addLast(saved);
		if (apply) {
			IrisCompat.setBypass(overrides.bypass().apply(saved.bypass()));
			IrisCompat.setSkipExtension(overrides.skipExtension().apply(saved.skipExtension()));
			IrisCompat.setRenderingLevel(overrides.renderingLevel().apply(saved.renderingLevel()));
		}
		if (PORTALS.size() == 1 && portalRouted) {
			// Before 1.21.5 draws go to whatever framebuffer is bound; bind the channel's target for the portal. The
			// level's view rotation and projection are not set this late in the frame, so set them too.
			restoreOutput = Gpu.bindForDrawing(portalChannel.prepareForDraw());
			PoseStack modelView = RenderSystem.getModelViewStack();
			modelView.pushPose();
			modelView.last().pose().load(VIEW_ROTATION);
			RenderSystem.applyModelViewMatrix();
			savedProjection = RenderSystem.getProjectionMatrix();
			RenderSystem.setProjectionMatrix(new Matrix4f(PROJECTION));
		}
		return portalRouted;
	}

	static void end() {
		Saved saved = PORTALS.pollLast();
		if (saved == null) {
			throw new IllegalStateException("EXIT: portal closed more often than opened");
		}
		if (saved.applied()) {
			IrisCompat.setBypass(saved.bypass());
			IrisCompat.setSkipExtension(saved.skipExtension());
			IrisCompat.setRenderingLevel(saved.renderingLevel());
		}
		if (PORTALS.isEmpty()) {
			if (portalRouted && restoreOutput != null) {
				if (savedProjection != null) {
					RenderSystem.setProjectionMatrix(savedProjection);
					savedProjection = null;
				}
				RenderSystem.getModelViewStack().popPose();
				RenderSystem.applyModelViewMatrix();
				restoreOutput.run();
			}
			restoreOutput = null;
			portalRouted = false;
			portalChannel = null;
		}
	}

	/** A portal off the render thread. It only sets this thread's vertex format switch. */
	static void portalOffThread(PortalOverrides overrides, Runnable body) {
		if (overrides.skipExtension() == PortalOverrides.Switch.KEEP) {
			body.run();
			return;
		}
		boolean previous = IrisCompat.skipExtension();
		IrisCompat.setSkipExtension(overrides.skipExtension().apply(previous));
		try {
			body.run();
		} finally {
			IrisCompat.setSkipExtension(previous);
		}
	}

	/** Whether a channel will draw this frame, so the scene depth has to be copied. */
	static boolean wantsSceneDepth() {
		if (!packActive()) {
			return false;
		}
		for (Channel channel : mergeOrder) {
			if (channel.wantsFrame()) {
				return true;
			}
		}
		return false;
	}

	/** A buffer source whose content is thrown away at the end of the frame. */
	static MultiBufferSource discard() {
		return FeatureDrawer.newBatch();
	}

	public static void plainFormat(Runnable build) {
		boolean previous = IrisCompat.skipExtension();
		IrisCompat.setSkipExtension(true);
		try {
			build.run();
		} finally {
			IrisCompat.setSkipExtension(previous);
		}
	}

	static void warnOnce(String key, String message) {
		if (WARNED.add(key)) {
			ExitMod.LOGGER.warn(message);
		}
	}

	/** Called when the level rendering starts. */
	public static void beginLevel(Camera camera, Matrix4f viewRotation, Matrix4f projection, float levelPartialTick) {
		if (inShadowPass()) {
			return;
		}
		inLevel = true;
		ExitRuntime.camera = camera;
		partialTick = levelPartialTick;
		VIEW_ROTATION.load(viewRotation);
		PROJECTION.load(projection);
	}

	/**
	 * Called when the level's frame graph starts. Before this the main depth still belongs to the previous frame, so
	 * no channel can be seeded from it.
	 */
	public static void beginPasses() {
		if (inLevel) {
			passesRunning = true;
		}
	}

	/** Called when the level rendering ends. Renders the batches, merges the channels and resets the frame. */
	public static void endLevel() {
		if (!inLevel) {
			return;
		}
		try {
			if (DebugShapes.isOn() && !inShadowPass()) {
				DebugShapes.atLevelEnd();
			}
			for (Channel channel : mergeOrder) {
				channel.drawBatch();
			}
			for (Channel channel : mergeOrder) {
				channel.foldTranslucentDepth();
			}
			// The merge compares the final main depth with the channel's; Iris 1.2 and older keep the pack's scene
			// depth in a texture of their own.
			SceneDepth.syncMainDepth();
			RenderTarget mainTarget = Gpu.mainTarget();
			boolean saved = IrisCompat.bypass();
			IrisCompat.setBypass(true);
			try {
				Channel debugged = DebugView.mode() != DebugView.Mode.OFF && packActive() ? find(DebugView.channel()) : null;
				if (debugged != null) {
					debugged.showDebugView(mainTarget);
				} else {
					// Blending channels become one layer when two or more drew. The others are merged over it.
					List<Channel> blending = new ArrayList<>();
					for (Channel channel : mergeOrder) {
						if (channel.options().blendWithChannels() && channel.drawnTarget() != null) {
							blending.add(channel);
						}
					}
					boolean any = !blending.isEmpty();
					if (blending.size() >= 2) {
						ChannelBlend.draw(mainTarget, blending);
						blendedFrames++;
					} else if (any) {
						blending.get(0).composite(mainTarget);
					}
					for (Channel channel : mergeOrder) {
						if (!channel.options().blendWithChannels()) {
							any |= channel.composite(mainTarget);
						}
					}
					if (any) {
						compositedFrames++;
					}
				}
			} finally {
				IrisCompat.setBypass(saved);
			}
		} finally {
			for (Channel channel : mergeOrder) {
				channel.endFrame();
			}
			inLevel = false;
			passesRunning = false;
			camera = null;
			SceneDepth.endFrame();
		}
	}

	public static void close() {
		for (Channel channel : mergeOrder) {
			channel.close();
		}
		SceneDepth.close();
		ChannelBlend.close();
		FeatureDrawer.close();
	}
}
