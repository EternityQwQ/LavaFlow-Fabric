package com.seibel.distanthorizons.neoforge;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Signature-only stub of Distant Horizons' NeoForge event proxy. Distant Horizons supplies the real
 * class at runtime; this exists so LavaFlow's compatibility mixin can be compiled without a Distant
 * Horizons artifact.
 */
public class NeoforgeClientProxy {
    public void afterLevelRenderEvent(RenderLevelStageEvent.AfterLevel event) {
        throw new AssertionError("Distant Horizons stub");
    }
}
