package ml.mypals.exit.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import ml.mypals.exit.api.Exit;
import ml.mypals.exit.api.ExitChannel;
import ml.mypals.exit.api.ExitOptions;
import ml.mypals.exit.api.PortalOverrides;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * The state behind {@link Exit}. It holds the channels, the camera and projection of the current frame, and the open
 * portals. Everything runs on the render thread, except {@link #plainFormat} and portals opened on other threads.
 */
public final class ExitRuntime {
	private static final Map<Identifier, Channel> CHANNELS = new LinkedHashMap<>();
	/** The channels sorted by priority, then by creation. */
	private static List<Channel> mergeOrder = List.of();
	private static @Nullable Channel main;

	private static boolean inLevel;
	/** Set once the level's frame graph runs. From then on the main target holds this frame's depth. */
	private static boolean passesRunning;
	private static @Nullable CameraRenderState camera;
	private static final Matrix4f VIEW_ROTATION = new Matrix4f();
	private static @Nullable GpuBufferSlice levelProjection;

	/** The portals open on the render thread, innermost last. They all belong to {@link #portalChannel}. */
	private static final ArrayDeque<Saved> PORTALS = new ArrayDeque<>();
	private static @Nullable Channel portalChannel;
	/** Whether the outermost portal routes. Nested portals follow it. */
	private static boolean portalRouted;

	/** The immediate drawing running now, or null. */
	static @Nullable DrawContext drawing;
	private static @Nullable SubmitNodeStorage discard;
	private static long compositedFrames;
	private static final Set<String> WARNED = new HashSet<>();

	private ExitRuntime() {}

	public static ExitChannel main() {
		if (main == null) {
			main = (Channel) channel(Exit.MAIN, ExitOptions.DEFAULT);
		}
		return main;
	}

	public static ExitChannel channel(Identifier id, ExitOptions options) {
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

	static @Nullable Channel find(Identifier id) {
		return CHANNELS.get(id);
	}

	static Set<Identifier> channelIds() {
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

	/** Counts render passes that a portal moved from the main target into a channel. Used by the checks. */
	private static long redirectedPasses;

	public static long redirectedPasses() {
		return redirectedPasses;
	}

	/**
	 * Called before a render pass is opened. While a portal routes, attachments on the main target are swapped for the
	 * channel's. Passes that are already open, and clears and copies, are not moved.
	 */
	public static RenderPassDescriptor redirectPass(RenderPassDescriptor descriptor) {
		if (!isRouting() || Gpu.isInRenderPass()) {
			return descriptor;
		}
		RenderTarget main = Gpu.mainTarget();
		GpuTextureView mainColor = main.getColorTextureView();
		GpuTextureView mainDepth = main.getDepthTextureView();
		boolean onMain = descriptor.depthAttachment() != null && descriptor.depthAttachment().textureView() == mainDepth;
		for (RenderPassDescriptor.Attachment<Optional<Vector4fc>> color : descriptor.colorAttachments()) {
			onMain |= color != null && color.textureView() == mainColor;
		}
		if (!onMain) {
			return descriptor;
		}
		// No pass is open yet, so the target may be cleared and seeded here.
		RenderTarget target = portalChannel.prepareForDraw();
		List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> colors = new ArrayList<>();
		for (RenderPassDescriptor.Attachment<Optional<Vector4fc>> color : descriptor.colorAttachments()) {
			colors.add(color != null && color.textureView() == mainColor
				? new RenderPassDescriptor.Attachment<>(target.getColorTextureView(), color.clearValue()) : color);
		}
		RenderPassDescriptor.Attachment<OptionalDouble> depth = descriptor.depthAttachment();
		if (depth != null && depth.textureView() == mainDepth) {
			depth = new RenderPassDescriptor.Attachment<>(target.getDepthTextureView(), depth.clearValue());
		}
		// The main target can be larger than the scene sized channel.
		RenderPass.RenderArea area = descriptor.renderArea();
		if (area != null) {
			int x = Math.min(area.x(), target.width);
			int y = Math.min(area.y(), target.height);
			area = new RenderPass.RenderArea(x, y, Math.min(area.width(), target.width - x), Math.min(area.height(), target.height - y));
		}
		redirectedPasses++;
		return new RenderPassDescriptor(descriptor.label(), colors, depth, area);
	}

	static CameraRenderState camera() {
		return camera;
	}

	static Matrix4fc viewRotation() {
		return VIEW_ROTATION;
	}

	static @Nullable GpuBufferSlice levelProjection() {
		return levelProjection;
	}

	private static long blendedFrames;
	private static long redirectedGizmos;

	/** Counts gizmos that portals moved into channels. Used by the checks. */
	public static long redirectedGizmos() {
		return redirectedGizmos;
	}

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
		Saved saved = new Saved(apply, IrisCompat.bypass(), IrisCompat.skipExtension(), IrisCompat.isRenderingLevel());
		PORTALS.addLast(saved);
		if (apply) {
			IrisCompat.setBypass(overrides.bypass().apply(saved.bypass()));
			IrisCompat.setSkipExtension(overrides.skipExtension().apply(saved.skipExtension()));
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
		}
		if (PORTALS.isEmpty()) {
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

	/** A collector whose content is thrown away at the end of the frame. */
	static SubmitNodeStorage discard() {
		if (discard == null) {
			discard = new SubmitNodeStorage();
		}
		return discard;
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
	public static void beginLevel(CameraRenderState cameraState) {
		if (inShadowPass()) {
			return;
		}
		inLevel = true;
		camera = cameraState;
		VIEW_ROTATION.set(cameraState.viewRotationMatrix);
		levelProjection = RenderSystem.getProjectionMatrixBuffer();
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
				redirectedGizmos += channel.submitGizmos();
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
			discard = null;
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
		discard = null;
	}
}
