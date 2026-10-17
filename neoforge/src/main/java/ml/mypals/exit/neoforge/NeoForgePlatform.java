package ml.mypals.exit.neoforge;

import ml.mypals.exit.platform.Platform;
import net.neoforged.fml.loading.FMLLoader;

public final class NeoForgePlatform implements Platform {
	@Override
	public boolean isModLoaded(String id) {
		// The loading mod list is ready before mixins are applied, unlike ModList.
		return FMLLoader.getCurrent().getLoadingModList().getModFileById(id) != null;
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return !FMLLoader.getCurrent().isProduction();
	}
}
