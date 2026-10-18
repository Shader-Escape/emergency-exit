package ml.mypals.exit.neoforge;

import ml.mypals.exit.impl.DebugShapes;
import ml.mypals.exit.impl.ExitMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
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
		NeoForge.EVENT_BUS.addListener(ClientStoppingEvent.class, event -> ExitMod.close());
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher().register(ExitMod.command(OUTPUT)));
		// The events of NeoForge for 1.21.8 have no buffer source, so the drawings are given none to pass to.
		NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterOpaqueBlocks.class, event -> {
			DebugShapes.inMainPass(null);
			DebugShapes.addToBatch(null, event.getCamera().getPosition());
		});
	}
}
