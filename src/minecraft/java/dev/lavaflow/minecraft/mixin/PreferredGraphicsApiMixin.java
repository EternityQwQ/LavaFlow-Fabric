package dev.lavaflow.minecraft.mixin;

import com.mojang.blaze3d.systems.GpuBackend;
import dev.lavaflow.minecraft.LavaFlowBackend;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PreferredGraphicsApi.class)
abstract class PreferredGraphicsApiMixin {
    @Inject(method = "getBackendsToTry", at = @At("HEAD"), cancellable = true)
    private void lavaflow$selectBackend(CallbackInfoReturnable<GpuBackend[]> callback) {
        callback.setReturnValue(new GpuBackend[]{new LavaFlowBackend()});
    }
}
