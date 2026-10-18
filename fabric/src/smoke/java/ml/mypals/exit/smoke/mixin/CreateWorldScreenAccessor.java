package ml.mypals.exit.smoke.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Presses the create world button for the smoke test. 1.16.5 has no {@code WorldCreationUiState} yet: the screen keeps
 * the name itself, and the game mode sits behind a plain button that cycles through the modes.
 */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
	@Invoker("onCreate")
	void emergency_exit$onCreate();

	@Accessor("nameEdit")
	EditBox emergency_exit$nameEdit();

	@Accessor("modeButton")
	Button emergency_exit$modeButton();
}
