package dev.lavaflow.vulkan;

final class SwapchainState {
    long handle;
    int imageFormat;
    int width;
    int height;
    long renderPass;
    long[] images = new long[0];
    long[] imageViews = new long[0];
    long[] framebuffers = new long[0];
    long[] imagesInFlight = new long[0];
    long[] renderFinishedSemaphores = new long[0];
}
