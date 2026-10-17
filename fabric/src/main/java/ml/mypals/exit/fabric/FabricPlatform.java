package ml.mypals.exit.fabric;

import ml.mypals.exit.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatform implements Platform {
	@Override
	public boolean isModLoaded(String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}
}
