package com.seibel.distanthorizons.neoforge.wrappers;

import com.mojang.blaze3d.textures.GpuTexture;

/**
 * Signature-only stub of Distant Horizons' NeoForge texture unwrapper. Distant Horizons supplies
 * the real class at runtime; this exists so LavaFlow's compatibility mixin can be compiled without
 * a Distant Horizons artifact.
 */
public class NeoforgeTextureUnwrapper {
    public static int getGlTextureIdFromGpuTexture(GpuTexture texture) throws ClassCastException {
        throw new AssertionError("Distant Horizons stub");
    }
}
