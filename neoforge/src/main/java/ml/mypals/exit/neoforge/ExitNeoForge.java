package ml.mypals.exit.neoforge;

import ml.mypals.exit.impl.DebugShapes;
import ml.mypals.exit.impl.ExitMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
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
		NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterOpaqueFeatures.class, event -> DebugShapes.inMainPass(null));
		NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, event ->
			DebugShapes.collectSubmits(event.getSubmitNodeCollector(), event.getLevelRenderState().cameraRenderState.pos));
	}
}
