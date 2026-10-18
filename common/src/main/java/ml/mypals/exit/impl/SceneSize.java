package ml.mypals.exit.impl;

import net.minecraft.client.Minecraft;

/**
 * The size the scene renders at. On this version there is no resolution-scaling mod (Super Resolution), so it is the
 * window size.
 */
public final class SceneSize {
	private SceneSize() {}

	public static boolean mergeStretched() {
		return false;
	}

	public static int width() {
		return Math.max(1, Minecraft.getInstance().getWindow().getWidth());
	}

	public static int height() {
		return Math.max(1, Minecraft.getInstance().getWindow().getHeight());
	}
}
