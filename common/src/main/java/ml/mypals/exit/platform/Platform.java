package ml.mypals.exit.platform;

import java.util.ServiceLoader;

/** What EXIT needs from the mod loader. Each loader provides it as a service. */
public interface Platform {
	Platform INSTANCE = ServiceLoader.load(Platform.class, Platform.class.getClassLoader()).findFirst()
		.orElseThrow(() -> new IllegalStateException("EXIT: no Platform service for this mod loader"));

	/** Whether a mod is loaded. It also works while mixins are being applied. */
	boolean isModLoaded(String id);

	boolean isDevelopmentEnvironment();
}
