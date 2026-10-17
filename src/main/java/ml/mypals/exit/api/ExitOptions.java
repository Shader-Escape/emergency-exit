package ml.mypals.exit.api;

import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * How a channel is merged back onto the frame. The options are fixed when the channel is created.
 *
 * @param occludedByWorld      The world hides drawings behind it. When false, drawings show through the world.
 * @param hideUnderHand        The first-person hand stays in front of the drawings.
 * @param dimBehindTranslucent Brightness from 0 to 1 of drawings behind water, glass and other translucent surfaces.
 *                             1 leaves them unchanged.
 * @param priority             Merge order. Higher priorities are drawn over lower ones. Among blending channels it
 *                             only matters at equal depth.
 * @param blendWithChannels    Blend by depth with the other blending channels, so the nearer one is in front. When
 *                             false, the channel is drawn over them by priority.
 * @param wantsFrame           Whether the channel will draw this frame, asked before the scene depth is copied. Null
 *                             means it drew in the previous frame. Returning false saves two depth copies.
 */
public record ExitOptions(boolean occludedByWorld, boolean hideUnderHand, float dimBehindTranslucent, int priority, boolean blendWithChannels,
	@Nullable BooleanSupplier wantsFrame) {
	/** Occluded by the world, under the hand, half as bright behind translucent surfaces, blended with other channels. */
	public static final ExitOptions DEFAULT = builder().build();

	public ExitOptions {
		if (!(dimBehindTranslucent >= 0 && dimBehindTranslucent <= 1)) {
			throw new IllegalArgumentException("dimBehindTranslucent must be within 0..1: " + dimBehindTranslucent);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private boolean occludedByWorld = true;
		private boolean hideUnderHand = true;
		private float dimBehindTranslucent = 0.5F;
		private int priority;
		private boolean blendWithChannels = true;
		private @Nullable BooleanSupplier wantsFrame;

		private Builder() {}

		public Builder occludedByWorld(boolean occludedByWorld) {
			this.occludedByWorld = occludedByWorld;
			return this;
		}

		public Builder hideUnderHand(boolean hideUnderHand) {
			this.hideUnderHand = hideUnderHand;
			return this;
		}

		public Builder dimBehindTranslucent(float brightness) {
			this.dimBehindTranslucent = brightness;
			return this;
		}

		public Builder priority(int priority) {
			this.priority = priority;
			return this;
		}

		public Builder blendWithChannels(boolean blendWithChannels) {
			this.blendWithChannels = blendWithChannels;
			return this;
		}

		public Builder wantsFrame(@Nullable BooleanSupplier wantsFrame) {
			this.wantsFrame = wantsFrame;
			return this;
		}

		public ExitOptions build() {
			return new ExitOptions(this.occludedByWorld, this.hideUnderHand, this.dimBehindTranslucent, this.priority, this.blendWithChannels, this.wantsFrame);
		}
	}
}
