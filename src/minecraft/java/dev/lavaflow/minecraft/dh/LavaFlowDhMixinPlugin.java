package dev.lavaflow.minecraft.dh;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Disables LavaFlow's Distant Horizons compatibility mixins when Distant Horizons is not installed. */
public final class LavaFlowDhMixinPlugin implements IMixinConfigPlugin {
    private static final String DH_MARKER =
            "com.seibel.distanthorizons.neoforge.wrappers.NeoforgeTextureUnwrapper";

    private boolean dhPresent;

    @Override
    public void onLoad(String mixinPackage) {
        dhPresent = isClassPresent(DH_MARKER);
        System.Logger logger = System.getLogger(LavaFlowDhMixinPlugin.class.getName());
        logger.log(dhPresent ? System.Logger.Level.INFO : System.Logger.Level.DEBUG,
                dhPresent
                        ? "Distant Horizons detected; applying LavaFlow compatibility mixins"
                        : "Distant Horizons not detected; skipping LavaFlow compatibility mixins");
    }

    private static boolean isClassPresent(String name) {
        // Resolved as a resource so the class is never initialized and no Distant Horizons code runs.
        String resource = name.replace('.', '/') + ".class";
        ClassLoader loader = LavaFlowDhMixinPlugin.class.getClassLoader();
        if (loader != null && loader.getResource(resource) != null) return true;
        return ClassLoader.getSystemClassLoader().getResource(resource) != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return dhPresent;
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
