package ml.mypals.exit.plugin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class ExitMixinPlugin implements IMixinConfigPlugin {
	private static final String PACKAGE = "ml.mypals.exit.mixin.";

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String name = mixinClassName.startsWith(PACKAGE) ? mixinClassName.substring(PACKAGE.length()) : mixinClassName;
		if (name.startsWith("iris.")) {
			return FabricLoader.getInstance().isModLoaded("iris");
		}
		if (name.equals("StrictValidationMixin")) {
			// Development only. A missing target class would make Mixin reject the whole config.
			return FabricLoader.getInstance().isDevelopmentEnvironment()
				&& ExitMixinPlugin.class.getClassLoader().getResource(targetClassName.replace('.', '/') + ".class") != null;
		}
		return true;
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
