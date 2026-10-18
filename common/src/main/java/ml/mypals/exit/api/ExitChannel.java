package ml.mypals.exit.api;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A layer of drawings that bypass the shader pack. They are drawn into the channel's own target and merged onto the
 * frame after the shader pack has finished it, as the {@link ExitOptions} say. Drawings of one channel hide each other
 * by depth. Get the shared channel with {@link Exit#main()}, or your own with {@link Exit#channel}.
 * <p>
 * Call it on the render thread while the level renders, for example from Fabric's {@code LevelRenderEvents}. Outside
 * that nothing is routed.
 */
public interface ExitChannel {
	ResourceLocation id();

	ExitOptions options();

	/**
	 * Runs {@code drawing} now with a collector, a camera-relative pose stack and the camera. What it submits goes to
	 * the channel's {@link #batch() batch}, so all of a channel's drawings are rendered together: the solid ones before
	 * the translucent ones, wherever they were submitted. Without a shader pack the batch renders into the main target.
	 * Nested calls share the outer collector. To draw with passes of your own, use a {@link #portal}.
	 */
	default void draw(Consumer<ExitContext> drawing) {
		this.draw(null, drawing);
	}

	/**
	 * Like {@link #draw(Consumer)}, but when nothing is routed the drawing submits to {@code passThrough}, so it is
	 * drawn as if EXIT was not there.
	 */
	void draw(@Nullable SubmitNodeCollector passThrough, Consumer<ExitContext> drawing);

	/** A portal with {@link PortalOverrides#DEFAULT}. */
	default void portal(Runnable body) {
		this.portal(PortalOverrides.DEFAULT, body);
	}

	/**
	 * Runs {@code body} now with the shader pack's switches set as {@code overrides} say, and puts them back
	 * afterwards. This is for code that has its own render context. Portals nest, but only on one channel at a time.
	 * <p>
	 * A portal draws at once, so the channel's batch comes after it and its solid drawings would cover translucent
	 * ones drawn in the portal. Call {@link #flushBatch()} in the portal before you draw translucent things.
	 * <p>
	 * While the portal routes, render passes opened in it on the main target draw into the channel's target instead.
	 * Use {@link #redirect} for anything else, such as other targets or a pass that was already open.
	 * <ul>
	 *     <li>It routes while the level renders with a shader pack, outside its shadow pass. Route only after the
	 *     level's render passes have started, for example in {@code END_MAIN}, and outside an open render pass.</li>
	 *     <li>The switches are set whenever a shader pack is in use outside its shadow pass.</li>
	 *     <li>In the shadow pass {@code body} does not run.</li>
	 *     <li>Off the render thread only {@link PortalOverrides#skipExtension()} is set, for that thread.</li>
	 * </ul>
	 */
	void portal(PortalOverrides overrides, Runnable body);

	/**
	 * The channel's target when the portal routes, otherwise {@code original}. Only needed for what a portal does not
	 * move by itself. Throws outside a portal of this channel and off the render thread.
	 */
	RenderTarget redirect(RenderTarget original);

	/**
	 * A collector for this frame's batch. What is submitted to it is rendered into the channel's target at the end of
	 * the level rendering, in one frame with the channel's {@link #draw drawings}: solid first, then translucent. Use
	 * camera-relative positions, and get it again each frame.
	 */
	default SubmitNodeCollector batch() {
		return this.batch(null);
	}

	/** Like {@link #batch()}, but returns {@code passThrough} when nothing is routed. */
	SubmitNodeCollector batch(@Nullable SubmitNodeCollector passThrough);

	/**
	 * Renders the batch now instead of at the end of the level rendering, for drawings that must come first, such as
	 * solid ones before the translucent ones of a {@link #portal}. Does nothing inside an open render pass.
	 */
	void flushBatch();
}
