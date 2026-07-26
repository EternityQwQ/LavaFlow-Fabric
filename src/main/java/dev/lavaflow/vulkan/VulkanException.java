package dev.lavaflow.vulkan;

public final class VulkanException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int result;

    public VulkanException(String operation, int result) {
        super(operation + " failed with VkResult " + result);
        this.result = result;
    }

    public int result() {
        return result;
    }

    public static void check(int result, String operation) {
        if (result != 0) {
            throw new VulkanException(operation, result);
        }
    }
}
