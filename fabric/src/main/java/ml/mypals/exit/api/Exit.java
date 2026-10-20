package ml.mypals.exit.api;

import com.mojang.blaze3d.pipeline.RenderTarget;
import ml.mypals.exit.impl.ExitRuntime;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Lets custom world rendering bypass the shader pack (Iris). Drawings go into a separate render target, which is
 * merged onto the frame by depth after the shader pack has finished it.
 * <ul>
 *     <li>{@link #draw} records drawings with a managed context. They are rendered with the batch.</li>
 *     <li>{@link #portal} runs code that has its own render context with the shader pack bypassed.</li>
 *     <li>{@link #batch} collects drawings and renders them at the end of the level rendering.</li>
 * </ul>
 * These use the shared {@link #main() main channel}. Use {@link #channel} for a channel with its own options. Without
 * a shader pack nothing is routed and everything is drawn as usual.
 */
public final class Exit {
	/** The id of the main channel. */
	public static final ResourceLocation MAIN = new ResourceLocation("emergency_exit", "main");

	private Exit() {}

	/** The shared channel, with {@link ExitOptions#DEFAULT}. */
	public static ExitChannel main() {
		return ExitRuntime.main();
	}

	/**
	 * Your own channel. The first call creates it, and later calls with the same id return it. Asking again with other
	 * options throws.
	 */
	public static ExitChannel channel(ResourceLocation id, ExitOptions options) {
		return ExitRuntime.channel(id, options);
	}

	/** {@link ExitChannel#draw(Consumer)} on the main channel. */
	public static void draw(Consumer<ExitContext> drawing) {
		main().draw(drawing);
	}

	/** {@link ExitChannel#draw(MultiBufferSource, Consumer)} on the main channel. */
	public static void draw(@Nullable MultiBufferSource passThrough, Consumer<ExitContext> drawing) {
		main().draw(passThrough, drawing);
	}

	/** {@link ExitChannel#portal(Runnable)} on the main channel. */
	public static void portal(Runnable body) {
		main().portal(body);
	}

	/** {@link ExitChannel#portal(PortalOverrides, Runnable)} on the main channel. */
	public static void portal(PortalOverrides overrides, Runnable body) {
		main().portal(overrides, body);
	}

	/** {@link ExitChannel#batch()} on the main channel. */
	public static MultiBufferSource batch() {
		return main().batch();
	}

	/** {@link ExitChannel#batch(MultiBufferSource)} on the main channel. */
	public static MultiBufferSource batch(@Nullable MultiBufferSource passThrough) {
		return main().batch(passThrough);
	}

	/** Whether drawings are routed now. That is while the level renders with a shader pack, outside its shadow pass. */
	public static boolean isActive() {
		return ExitRuntime.isActive();
	}

	/**
	 * {@link ExitChannel#redirect} for the channel whose portal is open now, for code that cannot reach the channel.
	 * Throws outside a portal and off the render thread. Code that also runs outside portals should check
	 * {@link #isRouting()} first.
	 */
	public static RenderTarget redirect(RenderTarget original) {
		return ExitRuntime.redirect(original);
	}

	/** Whether a portal is open and routes now. Always false off the render thread. */
	public static boolean isRouting() {
		return ExitRuntime.isRouting();
	}

	/** Whether a portal or a drawing is open now. Always false off the render thread. */
	public static boolean inPortal() {
		return ExitRuntime.inPortal();
	}

	/**
	 * Runs {@code build} with the shader pack's vertex format widening turned off, on any thread. Use it to build your
	 * own vertex buffers that are later drawn routed with vanilla pipelines. Unlike a portal it never routes and also
	 * runs in the shadow pass.
	 */
	public static void plainFormat(Runnable build) {
		ExitRuntime.plainFormat(build);
	}

	/**
	 * Runs {@code upload}, on the render thread. Upload buffers built with {@link #plainFormat} in it when that happens
	 * outside a portal.
	 * <p>
	 * On this Minecraft version it only runs {@code upload}: the Iris versions for it set the layout up from the format a
	 * buffer was built in, so the layouts already match. It is here for code that also runs on 1.21.1 to 1.21.4, where
	 * Iris 1.8 sets up its widened layouts when a buffer is uploaded and buffers built with {@code plainFormat} need it.
	 */
	public static void plainUpload(Runnable upload) {
		ExitRuntime.plainUpload(upload);
	}
}
