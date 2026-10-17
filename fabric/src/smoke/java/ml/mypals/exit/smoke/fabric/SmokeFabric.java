package ml.mypals.exit.smoke.fabric;

import ml.mypals.exit.smoke.SmokeTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class SmokeFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SmokeTest test = new SmokeTest();
		test.init("fabric");
		ClientTickEvents.END_CLIENT_TICK.register(test::tick);
	}
}
