package ml.mypals.exit.fabric;

import ml.mypals.exit.impl.DebugShapes;
import ml.mypals.exit.impl.ExitMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.MultiBufferSource;
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
		// Fabric API v1 client commands: before 1.19 there is no ClientCommandRegistrationCallback.
		ClientCommandManager.DISPATCHER.register(ExitMod.command(OUTPUT));
		// A null pass-through makes EXIT record the shapes into its own batches, drawn at the end of the
		// level rendering: the level buffer source is not flushed after this event on this version.
		WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
			DebugShapes.inMainPass(null);
			DebugShapes.collectSubmits(null, context.camera().getPosition());
		});
	}
}
