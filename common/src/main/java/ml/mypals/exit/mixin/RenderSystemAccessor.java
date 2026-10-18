package ml.mypals.exit.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Before 1.21.2 draws read RenderSystem's model-view matrix, which {@code applyModelViewMatrix()} replaces with a copy
 * of the model-view stack. Replacing it breaks entity rendering for the rest of the session (the first-person hand
 * vanishes), so EXIT sets its own matrix and puts the very same object back afterwards.
 */
@Mixin(RenderSystem.class)
public interface RenderSystemAccessor {
	@Accessor("modelViewMatrix")
	static Matrix4f emergency_exit$modelViewMatrix() {
		throw new AssertionError();
	}

	@Accessor("modelViewMatrix")
	static void emergency_exit$setModelViewMatrix(Matrix4f matrix) {
		throw new AssertionError();
	}
}
