package ml.mypals.exit.fabric;

import ml.mypals.exit.impl.DebugShapes;
import ml.mypals.exit.impl.ExitMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.network.chat.Component;

public final class ExitFabric implements ClientModInitializer {
	private static final ExitMod.Output<FabricClientCommandSource> OUTPUT = new ExitMod.Output<>() {
		@Override
		public void feedback(FabricClientCommandSource source, Component message) {
			source.sendFeedback(message);
		}

		@Override
		public void error(FabricClientCommandSource source, Component message) {
			source.sendError(message);
		}
	};

	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ExitMod.close());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ExitMod.command(OUTPUT)));
		// Before the entities are submitted. Null: EXIT records the shapes into its own batch, drawn at the end of the
		// frame. This Fabric API's context.consumers() is not flushed again after this callback, so geometry written to
		// it would be dropped.
		WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
			DebugShapes.inMainPass(null);
			DebugShapes.collectSubmits(null, context.camera().getPosition());
		});
	}
}
