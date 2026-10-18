package ml.mypals.exit.api;

/**
 * The Iris switches a {@link ExitChannel#portal portal} sets. Each one is restored when the portal ends.
 *
 * @param bypass         {@code ImmediateState.bypass}. The shader pack does not replace vanilla pipelines.
 * @param skipExtension  {@code ImmediateState.skipExtension}, per thread. Vertex buffers built now keep the vanilla
 *                       formats. Iris for 26.2 and older hands out its widened formats as the vanilla ones, so
 *                       EXIT leaves the switch alone and it does nothing on 1.21.10.
 * @param renderingLevel {@code ImmediateState.isRenderingLevel}. While it is on, Iris widens the formats of vertices
 *                       written by the submit pipeline.
 */
public record PortalOverrides(Switch bypass, Switch skipExtension, Switch renderingLevel) {
	/** Bypass on, format widening off, {@code isRenderingLevel} unchanged. */
	public static final PortalOverrides DEFAULT = new PortalOverrides(Switch.ON, Switch.ON, Switch.KEEP);

	public PortalOverrides {
		if (bypass == null || skipExtension == null || renderingLevel == null) {
			throw new IllegalArgumentException("PortalOverrides: a switch is null, use Switch.KEEP to leave it alone");
		}
	}

	public PortalOverrides withBypass(Switch bypass) {
		return new PortalOverrides(bypass, this.skipExtension, this.renderingLevel);
	}

	public PortalOverrides withSkipExtension(Switch skipExtension) {
		return new PortalOverrides(this.bypass, skipExtension, this.renderingLevel);
	}

	public PortalOverrides withRenderingLevel(Switch renderingLevel) {
		return new PortalOverrides(this.bypass, this.skipExtension, renderingLevel);
	}

	public enum Switch {
		/** Leave it as it is. */
		KEEP,
		ON,
		OFF;

		/** The value to set, given the current one. */
		public boolean apply(boolean current) {
			return switch (this) {
				case KEEP -> current;
				case ON -> true;
				case OFF -> false;
			};
		}
	}
}
