package ml.mypals.exit.neoforge;

import ml.mypals.exit.impl.DebugShapes;
import ml.mypals.exit.impl.ExitMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ExitMod.MOD_ID, dist = Dist.CLIENT)
public final class ExitNeoForge {
	private static final ExitMod.Output<CommandSourceStack> OUTPUT = new ExitMod.Output<>() {
		@Override
		public void feedback(CommandSourceStack source, Component message) {
			source.sendSuccess(() -> message, false);
		}

		@Override
		public void error(CommandSourceStack source, Component message) {
			source.sendFailure(message);
		}
	};

	public ExitNeoForge() {
		// NeoForge 21.4 has no ClientStoppingEvent; the game shutting down is the same moment on the client.
		NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class, event -> ExitMod.close());
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher().register(ExitMod.command(OUTPUT)));
		// The events of NeoForge for 1.21.5 have no buffer source, so the drawings are given none to pass to. There is one
		// event class with a stage; AFTER_CUTOUT_BLOCKS comes after the opaque terrain.
		NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, event -> {
			if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
				DebugShapes.inMainPass(null);
				DebugShapes.addToBatch(null, event.getCamera().getPosition());
			}
		});
	}
}
