package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import ml.mypals.exit.api.Exit;
import ml.mypals.exit.api.ExitChannel;
import ml.mypals.exit.api.ExitOptions;
import ml.mypals.exit.api.PortalOverrides;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
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
	/** Set once the level's frame graph runs. From then on the main target holds this frame's depth. */
	private static boolean passesRunning;
	private static @Nullable Camera camera;
	private static final Matrix4f VIEW_ROTATION = new Matrix4f();
	private static final Matrix4f PROJECTION = new Matrix4f();
	/** The projection RenderSystem had when the level rendering started, or null outside it. */
	private static @Nullable Matrix4f levelProjection;
	/** What {@code getMainRenderTarget()} returns while EXIT renders a batch, see {@link #targetFor}. */
	private static @Nullable RenderTarget output;
	private static float partialTick;

	/** The portals open on the render thread, innermost last. They all belong to {@link #portalChannel}. */
	private static final ArrayDeque<Saved> PORTALS = new ArrayDeque<>();
	private static @Nullable Channel portalChannel;
	/** Whether the outermost portal routes. Nested portals follow it. */
	private static boolean portalRouted;

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
		RenderSystem.assertOnRenderThread();
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

	/** Counts the times a portal handed out a channel's target for the main one. Used by the checks. */
	private static long redirectedPasses;
	/** While true, {@link #targetFor} hands out the real main target: preparing a channel runs code that asks for it. */
	private static boolean preparing;

	public static long redirectedPasses() {
		return redirectedPasses;
	}

	/** EXIT's full-screen programs that did not compile or link. Used by the checks. */
	public static List<String> brokenPipelines() {
		return Gpu.brokenPipelines();
	}

	/**
	 * What {@code Minecraft.getMainRenderTarget()} returns (MinecraftMixin). Before 1.21.5 everything draws into the
	 * bound framebuffer, and vanilla binds the main target through this getter (render types' {@code MAIN_TARGET}
	 * output, among others). While EXIT renders a batch it is the batch's target, and while a portal routes it is the
	 * channel's target; otherwise the main target.
	 */
	public static RenderTarget targetFor(RenderTarget main) {
		if (preparing || !RenderSystem.isOnRenderThread()) {
			return main;
		}
		if (output != null) {
			return output;
		}
		if (!isRouting()) {
			return main;
		}
		redirectedPasses++;
		preparing = true;
		try {
			return portalChannel.prepareForDraw();
		} finally {
			preparing = false;
		}
	}

	/** The main render target. */
	public static RenderTarget mainTarget() {
		return Gpu.mainTarget();
	}

	/** Makes {@code getMainRenderTarget()} return {@code target} instead, or stops it with null. */
	static void setOutput(@Nullable RenderTarget target) {
		output = target;
	}

	static @Nullable RenderTarget output() {
		return output;
	}

	static Camera camera() {
		return camera;
	}

	/** The level's projection. The camera does not hold it before 26.1. */
	static Matrix4fc projectionMatrix() {
		return PROJECTION;
	}

	static Matrix4fc viewRotation() {
		return VIEW_ROTATION;
	}

	/** The partial tick the level renders with. */
	static float partialTick() {
		return partialTick;
	}

	static @Nullable Matrix4f levelProjection() {
		return levelProjection;
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

	/**
	 * The switches a portal found when it opened. They are restored when it ends, if it set them. {@code extendedSetup}
	 * is Iris's renderWithExtendedVertexFormat, which follows skipExtension (see {@link #plainUpload}).
	 */
	private record Saved(boolean applied, boolean bypass, boolean skipExtension, boolean renderingLevel, boolean extendedSetup) {}

	/**
	 * Opens a portal on {@code channel} and returns whether it routes. Null overrides mean the defaults, or no change
	 * inside another portal. The switches are only set while a shader pack is in use outside its shadow pass.
	 */
	static boolean begin(Channel channel, @Nullable PortalOverrides overrides) {
		RenderSystem.assertOnRenderThread();
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
		Saved saved = new Saved(apply, IrisCompat.bypass(), IrisCompat.skipExtension(), IrisCompat.isRenderingLevel(),
			IrisCompat.renderWithExtendedVertexFormat());
		PORTALS.addLast(saved);
		if (apply) {
			IrisCompat.setBypass(overrides.bypass().apply(saved.bypass()));
			boolean skipExtension = overrides.skipExtension().apply(saved.skipExtension());
			IrisCompat.setSkipExtension(skipExtension);
			if (skipExtension) {
				// Buffers built in the portal are vanilla, so the ones uploaded in it get the vanilla layout.
				IrisCompat.setRenderWithExtendedVertexFormat(false);
			}
			IrisCompat.setRenderingLevel(overrides.renderingLevel().apply(saved.renderingLevel()));
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
			IrisCompat.setRenderWithExtendedVertexFormat(saved.extendedSetup());
		}
		if (PORTALS.isEmpty()) {
			portalRouted = false;
			portalChannel = null;
		}
	}

	/**
	 * A portal off the render thread. It only sets this thread's vertex format switch: the layout switch is global, and
	 * vertex buffers are uploaded on the render thread.
	 */
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

	/** A buffer source that throws away what is drawn into it. */
	static MultiBufferSource discard() {
		return BatchDrawer.DISCARD;
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

	public static void plainUpload(Runnable upload) {
		RenderSystem.assertOnRenderThread();
		boolean previous = IrisCompat.renderWithExtendedVertexFormat();
		IrisCompat.setRenderWithExtendedVertexFormat(false);
		try {
			upload.run();
		} finally {
			IrisCompat.setRenderWithExtendedVertexFormat(previous);
		}
	}

	static void warnOnce(String key, String message) {
		if (WARNED.add(key)) {
			ExitMod.LOGGER.warn(message);
		}
	}

	/** Called when the level rendering starts. */
	public static void beginLevel(Camera levelCamera, Matrix4fc viewRotation, Matrix4fc projection, float levelPartialTick) {
		if (inShadowPass()) {
			return;
		}
		inLevel = true;
		camera = levelCamera;
		partialTick = levelPartialTick;
		VIEW_ROTATION.set(viewRotation);
		PROJECTION.set(projection);
		levelProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
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
			levelProjection = null;
			SceneDepth.endFrame();
		}
	}

	public static void close() {
		for (Channel channel : mergeOrder) {
			channel.close();
		}
		SceneDepth.close();
		ChannelBlend.close();
	}
}
