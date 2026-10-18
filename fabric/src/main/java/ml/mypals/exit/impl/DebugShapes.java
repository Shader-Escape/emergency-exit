package ml.mypals.exit.impl;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ml.mypals.exit.api.Exit;
import ml.mypals.exit.api.ExitChannel;
import ml.mypals.exit.api.ExitOptions;
import ml.mypals.exit.api.PortalOverrides;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Test boxes for {@code /emergency_exit debug shapes}, placed 5 blocks in front of the player.
 * <ul>
 *     <li>Red, on the right, is a translucent drawing on the main channel. It is recorded into the batch, so the
 *     green box does not cover it although it is drawn with the batch.</li>
 *     <li>Green, in the middle, is drawn with the main channel's batch.</li>
 *     <li>Yellow sits behind the green box on another channel. The channels blend by depth, so green covers it where
 *     they overlap.</li>
 *     <li>Blue, on the left, is a drawing on the main target inside a portal. The portal moves the frame into a channel
 *     that shows through the world.</li>
 * </ul>
 */
public final class DebugShapes {
	public static final double DISTANCE = 5;
	private static final ResourceLocation WHITE = ExitMod.id("textures/misc/white.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	/** The centre of the middle box, or null while the shapes are off. */
	private static @Nullable Vec3 center;
	/** The player's horizontal right when the shapes were turned on. */
	private static Vec3 right = new Vec3(-1, 0, 0);
	/** The player's horizontal forward direction when the shapes were turned on. */
	private static Vec3 forward = new Vec3(0, 0, 1);
	private static @Nullable ExitChannel xray;
	private static @Nullable ExitChannel mix;
	private static final Stats STATS = new Stats();

	/** Counters for the checks. */
	public static final class Stats {
		public int draws;
		public int routedDraws;
		public int batchDraws;
		public int portalDraws;
		/** Portals whose drawing on the main target landed in the channel. */
		public int portalRedirectedPasses;
		public int portalRoutingMismatches;
		/** Redirects outside a portal that did not throw. Should stay 0. */
		public int unguardedRedirects;
		/** Whether the portal on a worker thread behaved. Null until it ran. */
		public volatile @Nullable Boolean offThreadOk;
	}

	private DebugShapes() {}

	/**
	 * Called after the solid features. {@code passThrough} is the level's buffer source, used when nothing is routed.
	 */
	public static void inMainPass(@Nullable MultiBufferSource passThrough) {
		Vec3 at = center;
		if (at == null) {
			return;
		}
		Exit.draw(passThrough, exit -> {
			STATS.draws++;
			if (exit.routed()) {
				STATS.routedDraws++;
			}
			box(exit.bufferSource(), exit.poseStack(), exit.cameraPos(), at.add(right.scale(2)), 0xC0FF3030, false);
		});
	}

	/** Called while the level collects its submits. The green box goes to the main channel's batch. */
	public static void collectSubmits(@Nullable MultiBufferSource source, Vec3 camera) {
		Vec3 at = center;
		if (at == null) {
			return;
		}
		STATS.batchDraws++;
		box(Exit.batch(source), new PoseStack(), camera, at, 0xFF30FF30, true);
	}

	/** Called at the end of the level rendering, before the batches are drawn. */
	static void atLevelEnd() {
		Vec3 at = center;
		if (at == null) {
			return;
		}
		// A channel of its own, blended with the main channel by depth.
		if (mix == null) {
			mix = Exit.channel(ExitMod.id("debug_mix"), ExitOptions.DEFAULT);
		}
		mix.draw(exit -> box(exit.bufferSource(), exit.poseStack(), exit.cameraPos(),
			at.add(forward.scale(0.5)).add(right.scale(0.5)).add(0, 0.3, 0), 0xFFFFE030, true));

		// A portal around code with its own render context.
		if (xray == null) {
			xray = Exit.channel(ExitMod.id("debug_xray"), ExitOptions.builder().occludedByWorld(false).priority(10).build());
			checkOffThread();
		}
		checkRedirectGuard();
		xray.portal(() -> {
			STATS.portalDraws++;
			boolean routing = Exit.isRouting();
			boolean redirected = xray.redirect(Gpu.mainTarget()) != Gpu.mainTarget();
			if (redirected != routing) {
				STATS.portalRoutingMismatches++;
			}
			if (redirected) {
				STATS.portalRedirectedPasses++;
			}
			// Drawn into the main target without redirect. The portal bound the channel's framebuffer, so it lands there.
			MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(new BufferBuilder(1536));
			box(immediate, new PoseStack(), ExitRuntime.camera().getPosition(), at.subtract(right.scale(2)), 0xC03030FF, false);
			immediate.endBatch();
		});
	}

	/** Checks that redirect throws outside a portal. */
	private static void checkRedirectGuard() {
		try {
			Exit.redirect(Gpu.mainTarget());
			STATS.unguardedRedirects++;
		} catch (IllegalStateException expected) {
		}
		try {
			xray.redirect(Gpu.mainTarget());
			STATS.unguardedRedirects++;
		} catch (IllegalStateException expected) {
		}
	}

	/** Checks that a portal on a worker thread only sets that thread's format switch, and that redirect throws. */
	private static void checkOffThread() {
		Thread worker = new Thread(() -> {
			boolean before = IrisCompat.skipExtension();
			boolean[] ok = {true};
			xray.portal(PortalOverrides.DEFAULT, () -> {
				ok[0] &= IrisCompat.skipExtension() == IrisCompat.hasSkipExtension();
				try {
					Exit.redirect(Gpu.mainTarget());
					ok[0] = false;
				} catch (IllegalStateException expected) {
				}
			});
			STATS.offThreadOk = ok[0] && IrisCompat.skipExtension() == before;
		}, "emergency_exit-portal-check");
		worker.setDaemon(true);
		worker.start();
	}

	public static boolean isOn() {
		return center != null;
	}

	/** Turns the shapes on in front of {@code viewer}, or off. Returns whether they are on now. */
	public static boolean toggle(Entity viewer) {
		if (center != null) {
			center = null;
			return false;
		}
		Vec3 look = viewer.getLookAngle();
		center = viewer.getEyePosition().add(look.scale(DISTANCE));
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() > 1.0E-6) {
			flat = flat.normalize();
			forward = flat;
			right = new Vec3(-flat.z, 0, flat.x);
		}
		return true;
	}

	public static @Nullable Vec3 center() {
		return center;
	}

	public static Stats stats() {
		return STATS;
	}

	/** A translucent box of one block centred at {@code at}. It writes no depth. */
	private static void box(MultiBufferSource source, PoseStack poseStack, Vec3 camera, Vec3 at, int argb) {
		box(source, poseStack, camera, at, argb, false);
	}

	/** A box of one block centred at {@code at}. A solid box is opaque and writes depth. */
	private static void box(MultiBufferSource source, PoseStack poseStack, Vec3 camera, Vec3 at, int argb, boolean solid) {
		// 1.19 added debugQuads(); before it there is no untextured quad render type, so the translucent box uses the
		// entity one with EXIT's own white texture (see the ChangeLog).
		VertexConsumer buffer = source.getBuffer(solid ? RenderType.entitySolid(WHITE) : RenderType.entityTranslucent(WHITE));
		poseStack.pushPose();
		poseStack.translate(at.x - 0.5 - camera.x, at.y - 0.5 - camera.y, at.z - 0.5 - camera.z);
		PoseStack.Pose pose = poseStack.last();
		quad(buffer, pose, argb, solid, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0);
		quad(buffer, pose, argb, solid, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 1, 0);
		quad(buffer, pose, argb, solid, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0);
		quad(buffer, pose, argb, solid, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1);
		quad(buffer, pose, argb, solid, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0);
		quad(buffer, pose, argb, solid, 0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1);
		poseStack.popPose();
	}

	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int argb, boolean solid, float... corners) {
		for (int i = 0; i < 12; i += 3) {
			vertex(buffer, pose, argb, corners, i);
		}
		if (solid) {
			// The solid render type culls back faces, so draw the other side too.
			for (int i = 9; i >= 0; i -= 3) {
				vertex(buffer, pose, argb, corners, i);
			}
		}
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int argb, float[] corners, int i) {
		// Both render types use the entity format here, so every element is written.
		buffer.vertex(pose.pose(), corners[i], corners[i + 1], corners[i + 2]).color(argb)
			.uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(pose.normal(), 0, 1, 0)
			.endVertex();
	}
}
