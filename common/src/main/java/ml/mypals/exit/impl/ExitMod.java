package ml.mypals.exit.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import ml.mypals.exit.api.Exit;
import ml.mypals.exit.compat.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The parts of the mod that both loaders share. The loader entry points call into it. */
public final class ExitMod {
	public static final String MOD_ID = "emergency_exit";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private ExitMod() {}

	/** How a loader's command source shows messages. */
	public interface Output<S> {
		void feedback(S source, Component message);

		void error(S source, Component message);
	}

	/** Called when the client stops. */
	public static void close() {
		ExitRuntime.close();
	}

	/** The {@code /emergency_exit} client command. */
	public static <S> LiteralArgumentBuilder<S> command(Output<S> output) {
		LiteralArgumentBuilder<S> debug = LiteralArgumentBuilder.<S>literal("debug")
			.executes(context -> {
				output.feedback(context.getSource(), Component.literal("EXIT debug view: " + DebugView.mode().id() + " of " + DebugView.channel()));
				for (DebugView.Mode mode : DebugView.Mode.values()) {
					output.feedback(context.getSource(), Component.literal("  " + mode.id() + " - " + mode.description));
				}
				output.feedback(context.getSource(), Component.literal("channels: " + ExitRuntime.channelIds()));
				return 1;
			});

		for (DebugView.Mode mode : DebugView.Mode.values()) {
			debug.then(LiteralArgumentBuilder.<S>literal(mode.id())
				.executes(context -> setView(output, context, mode, Exit.MAIN))
				.then(RequiredArgumentBuilder.<S, String>argument("channel", StringArgumentType.greedyString())
					.suggests((context, builder) -> {
						ExitRuntime.channelIds().forEach(id -> builder.suggest(id.toString()));
						return builder.buildFuture();
					})
					.executes(context -> {
						ResourceLocation channel = ResourceLocation.tryParse(StringArgumentType.getString(context, "channel"));
						if (channel == null || ExitRuntime.find(channel) == null) {
							output.error(context.getSource(), Component.literal("No such EXIT channel. Channels: " + ExitRuntime.channelIds()));
							return 0;
						}
						return setView(output, context, mode, channel);
					})));
		}

		debug.then(LiteralArgumentBuilder.<S>literal("shapes").executes(context -> {
			boolean on = DebugShapes.toggle(Minecraft.getInstance().player);
			output.feedback(context.getSource(), Component.literal(on
				? "EXIT test shapes on, " + (int) DebugShapes.DISTANCE + " blocks ahead: red = translucent drawing, green = batch, yellow = another channel blended by depth (behind green), blue = portal on a see-through channel"
				: "EXIT test shapes off"));
			if (on && !IrisCompat.isPackInUse()) {
				output.feedback(context.getSource(), Component.literal("No shader pack is in use, so the shapes are drawn as usual, without EXIT."));
			}
			return 1;
		}));

		return LiteralArgumentBuilder.<S>literal(MOD_ID).then(debug);
	}

	private static <S> int setView(Output<S> output, CommandContext<S> context, DebugView.Mode mode, ResourceLocation channel) {
		DebugView.set(mode, channel);
		output.feedback(context.getSource(), Component.literal("EXIT debug view: " + mode.id() + " of " + channel + " (" + mode.description + ")"));
		if (mode != DebugView.Mode.OFF && !IrisCompat.isPackInUse()) {
			output.feedback(context.getSource(), Component.literal("No shader pack is in use, so nothing is routed and the view will not show."));
		}
		return 1;
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
