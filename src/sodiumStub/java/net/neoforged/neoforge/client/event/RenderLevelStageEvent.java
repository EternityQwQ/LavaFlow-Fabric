package net.neoforged.neoforge.client.event;

/**
 * Signature-only stub of NeoForge's level render stage event. NeoForge supplies the real classes at
 * runtime; this exists so LavaFlow's Distant Horizons compatibility mixin can be compiled without a
 * NeoForge artifact.
 */
public abstract class RenderLevelStageEvent {
    public static class AfterLevel extends RenderLevelStageEvent {}
}
