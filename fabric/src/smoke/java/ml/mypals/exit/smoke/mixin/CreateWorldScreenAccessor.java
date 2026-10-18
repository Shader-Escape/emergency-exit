package ml.mypals.exit.smoke.mixin;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Presses the create world button for the smoke test. 1.17 has no {@code WorldCreationUiState} yet: the screen keeps
 * the name and the game mode itself.
 */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
	@Invoker("onCreate")
	void emergency_exit$onCreate();

	@Accessor("nameEdit")
	EditBox emergency_exit$nameEdit();

	@Accessor("modeButton")
	CycleButton<?> emergency_exit$modeButton();
}
