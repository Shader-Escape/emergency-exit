package ml.mypals.exit.smoke.neoforge;

import ml.mypals.exit.smoke.SmokeTest;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "emergency_exit_smoke", dist = Dist.CLIENT)
public final class SmokeNeoForge {
	public SmokeNeoForge() {
		SmokeTest test = new SmokeTest();
		test.init("neoforge");
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> test.tick(Minecraft.getInstance()));
	}
}
