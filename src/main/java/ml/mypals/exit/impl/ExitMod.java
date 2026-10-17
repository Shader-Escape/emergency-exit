package ml.mypals.exit.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import ml.mypals.exit.api.Exit;
import ml.mypals.exit.compat.IrisCompat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExitMod implements ClientModInitializer {
	public static final String MOD_ID = "emergency_exit";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ExitRuntime.close());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(buildCommand()));
		DebugShapes.register();
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand() {
		LiteralArgumentBuilder<FabricClientCommandSource> debug = ClientCommands.literal("debug")
			.executes(context -> {
				context.getSource().sendFeedback(Component.literal("EXIT debug view: " + DebugView.mode().id() + " of " + DebugView.channel()));
				for (DebugView.Mode mode : DebugView.Mode.values()) {
					context.getSource().sendFeedback(Component.literal("  " + mode.id() + " - " + mode.description));
				}
				context.getSource().sendFeedback(Component.literal("channels: " + ExitRuntime.channelIds()));
				return 1;
			});

		for (DebugView.Mode mode : DebugView.Mode.values()) {
			debug.then(ClientCommands.literal(mode.id())
				.executes(context -> setView(context, mode, Exit.MAIN))
				.then(ClientCommands.argument("channel", StringArgumentType.greedyString())
					.suggests((context, builder) -> {
						ExitRuntime.channelIds().forEach(id -> builder.suggest(id.toString()));
						return builder.buildFuture();
					})
					.executes(context -> {
						Identifier channel = Identifier.tryParse(StringArgumentType.getString(context, "channel"));
						if (channel == null || ExitRuntime.find(channel) == null) {
							context.getSource().sendError(Component.literal("No such EXIT channel; channels: " + ExitRuntime.channelIds()));
							return 0;
						}
						return setView(context, mode, channel);
					})));
		}

		debug.then(ClientCommands.literal("shapes").executes(context -> {
			boolean on = DebugShapes.toggle(context.getSource().getPlayer());
			context.getSource().sendFeedback(Component.literal(on
				? "EXIT test shapes on, " + (int) DebugShapes.DISTANCE + " blocks ahead: red = immediate drawing, green = batch, yellow = another channel blended by depth (behind green), blue = portal on a see-through channel, cyan outline = gizmo in that portal"
				: "EXIT test shapes off"));
			if (on && !IrisCompat.isPackInUse()) {
				context.getSource().sendFeedback(Component.literal("No shader pack is in use: the shapes are drawn as usual, without EXIT."));
			}
			return 1;
		}));

		return ClientCommands.literal(MOD_ID).then(debug);
	}

	private static int setView(CommandContext<FabricClientCommandSource> context, DebugView.Mode mode, Identifier channel) {
		DebugView.set(mode, channel);
		context.getSource().sendFeedback(Component.literal("EXIT debug view: " + mode.id() + " of " + channel + " (" + mode.description + ")"));
		if (mode != DebugView.Mode.OFF && !IrisCompat.isPackInUse()) {
			context.getSource().sendFeedback(Component.literal("No shader pack is in use: nothing is routed, so the view will not show."));
		}
		return 1;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
