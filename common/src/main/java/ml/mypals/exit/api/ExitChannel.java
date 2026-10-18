package ml.mypals.exit.api;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A layer of drawings that bypass the shader pack. They are drawn into the channel's own target and merged onto the
 * frame after the shader pack has finished it, as the {@link ExitOptions} say. Drawings of one channel hide each other
 * by depth. Get the shared channel with {@link Exit#main()}, or your own with {@link Exit#channel}.
 * <p>
 * Call it on the render thread while the level renders, for example from Fabric's {@code WorldRenderEvents}. Outside
 * that nothing is routed.
 */
public interface ExitChannel {
	ResourceLocation id();

	ExitOptions options();

	/**
	 * Runs {@code drawing} now with a buffer source, a camera-relative pose stack and the camera. What it draws into
	 * the buffer source goes to the channel's {@link #batch() batch}, so all of a channel's drawings are rendered
	 * together: the solid render types before the translucent ones, wherever they were drawn. Without a shader pack the
	 * batch renders into the main target. Nested calls share the outer buffer source. To draw with shaders and targets
	 * of your own, use a {@link #portal}.
	 */
	default void draw(Consumer<ExitContext> drawing) {
		this.draw(null, drawing);
	}

	/**
	 * Like {@link #draw(Consumer)}, but when nothing is routed the drawing draws into {@code passThrough}, so it is
	 * drawn as if EXIT was not there.
	 */
	void draw(@Nullable MultiBufferSource passThrough, Consumer<ExitContext> drawing);

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
	 * While the portal routes, the channel's target is bound and {@code Minecraft.getMainRenderTarget()} returns it, so
	 * drawings into the main target go to the channel. Use {@link #redirect} for anything else, such as other targets or
	 * a main target fetched before the portal began.
	 * <ul>
	 *     <li>It routes while the level renders with a shader pack, outside its shadow pass. Route only after the
	 *     level has started drawing, for example in {@code LAST}.</li>
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
	 * A buffer source for this frame's batch. What is drawn into it is kept, one buffer per render type, and rendered
	 * into the channel's target at the end of the level rendering, in one frame with the channel's
	 * {@link #draw drawings}: solid render types first, then translucent ones. Use camera-relative positions, and get it
	 * again each frame. Do not end its batches yourself.
	 */
	default MultiBufferSource batch() {
		return this.batch(null);
	}

	/** Like {@link #batch()}, but returns {@code passThrough} when nothing is routed. */
	MultiBufferSource batch(@Nullable MultiBufferSource passThrough);

	/**
	 * Renders the batch now instead of at the end of the level rendering, for drawings that must come first, such as
	 * solid ones before the translucent ones of a {@link #portal}.
	 */
	void flushBatch();
}
