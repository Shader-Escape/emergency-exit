package ml.mypals.exit.smoke.mixin;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Presses the create world button for the smoke test. 1.19.3 has no {@code WorldCreationUiState}: the screen keeps the
 * world name and the game mode itself, so the test sets them through these.
 */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
	@Invoker("onCreate")
	void emergency_exit$onCreate();

	@Accessor("nameEdit")
	EditBox emergency_exit$nameEdit();

	/** The game mode button. Its value type is package-private, so the test presses it until it reads CREATIVE. */
	@Accessor("modeButton")
	CycleButton<?> emergency_exit$modeButton();
}
