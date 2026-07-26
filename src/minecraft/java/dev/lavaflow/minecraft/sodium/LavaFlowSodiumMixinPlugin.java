package dev.lavaflow.minecraft.sodium;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Disables LavaFlow's Sodium compatibility mixins when Sodium is not installed. */
public final class LavaFlowSodiumMixinPlugin implements IMixinConfigPlugin {
    private static final String SODIUM_MARKER =
            "net.caffeinemc.mods.sodium.client.gpu.device.backend.DrawBackend";

    private boolean sodiumPresent;

    @Override
    public void onLoad(String mixinPackage) {
        sodiumPresent = isClassPresent(SODIUM_MARKER);
        System.Logger logger = System.getLogger(LavaFlowSodiumMixinPlugin.class.getName());
        logger.log(sodiumPresent ? System.Logger.Level.INFO : System.Logger.Level.DEBUG,
                sodiumPresent
                        ? "Sodium detected; applying LavaFlow Sodium compatibility mixins"
                        : "Sodium not detected; skipping LavaFlow Sodium compatibility mixins");
    }

    private static boolean isClassPresent(String name) {
        // Resolved as a resource so the class is never initialized and no Sodium code runs here.
        String resource = name.replace('.', '/') + ".class";
        ClassLoader loader = LavaFlowSodiumMixinPlugin.class.getClassLoader();
        if (loader != null && loader.getResource(resource) != null) return true;
        return ClassLoader.getSystemClassLoader().getResource(resource) != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return sodiumPresent;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
