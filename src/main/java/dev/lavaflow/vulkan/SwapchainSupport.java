package dev.lavaflow.vulkan;

import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;

final class SwapchainSupport implements AutoCloseable {
    final VkSurfaceCapabilitiesKHR capabilities;
    final VkSurfaceFormatKHR.Buffer formats;
    final int[] presentModes;

    SwapchainSupport(
            VkSurfaceCapabilitiesKHR capabilities,
            VkSurfaceFormatKHR.Buffer formats,
            int[] presentModes
    ) {
        this.capabilities = capabilities;
        this.formats = formats;
        this.presentModes = presentModes;
    }

    @Override
    public void close() {
        formats.free();
        capabilities.free();
    }
}
