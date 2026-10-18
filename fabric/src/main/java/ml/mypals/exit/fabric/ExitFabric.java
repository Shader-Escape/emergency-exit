package ml.mypals.exit.fabric;

import ml.mypals.exit.impl.DebugShapes;
import ml.mypals.exit.impl.ExitMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ExitMod.command(OUTPUT)));
		// Before the entities are submitted, so what goes to the level's buffer source is still drawn this frame.
		WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
			MultiBufferSource consumers = context.consumers();
			if (consumers == null) {
				return;
			}
			DebugShapes.inMainPass(consumers);
			DebugShapes.collectSubmits(consumers, context.camera().getPosition());
		});
	}
}
