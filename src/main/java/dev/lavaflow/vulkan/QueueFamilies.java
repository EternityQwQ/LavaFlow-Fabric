package dev.lavaflow.vulkan;

record QueueFamilies(int graphics, int present) {
    boolean isComplete() {
        return graphics >= 0 && present >= 0;
    }

    boolean isUnified() {
        return graphics == present;
    }
}
