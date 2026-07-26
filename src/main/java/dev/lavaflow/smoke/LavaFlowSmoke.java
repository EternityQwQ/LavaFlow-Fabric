package dev.lavaflow.smoke;

import dev.lavaflow.vulkan.LavaFlowRenderer;
import org.lwjgl.glfw.GLFWErrorCallback;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public final class LavaFlowSmoke {
    private LavaFlowSmoke() {
    }

    public static void main(String[] args) {
        long frameLimit = parseFrameLimit(args);
        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err);
        errorCallback.set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        long window = NULL;
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            window = glfwCreateWindow(960, 540, "LavaFlow Vulkan 1.1", NULL, NULL);
            if (window == NULL) {
                throw new IllegalStateException("Unable to create a Vulkan-capable GLFW window");
            }
            glfwShowWindow(window);
            glfwPollEvents();
            run(window, frameLimit);
        } finally {
            if (window != NULL) {
                glfwDestroyWindow(window);
            }
            glfwTerminate();
            errorCallback.free();
        }
    }

    private static void run(long window, long frameLimit) {
        try (LavaFlowRenderer renderer = new LavaFlowRenderer(window)) {
            glfwSetFramebufferSizeCallback(window, (ignored, width, height) -> renderer.markFramebufferResized());
            long frames = 0;
            double lastTitleUpdate = glfwGetTime();
            while (!glfwWindowShouldClose(window) && (frameLimit == 0 || frames < frameLimit)) {
                glfwPollEvents();
                double time = glfwGetTime();
                float pulse = (float) (Math.sin(time * 0.8) * 0.5 + 0.5);
                renderer.render(0.04f + pulse * 0.08f, 0.08f + pulse * 0.06f, 0.10f + pulse * 0.12f);
                frames++;
                if (time - lastTitleUpdate >= 1.0) {
                    glfwSetWindowTitle(window,
                            "LavaFlow Vulkan 1.1 | " + renderer.deviceName() + " | frames " + frames);
                    lastTitleUpdate = time;
                }
            }
        }
    }

    private static long parseFrameLimit(String[] args) {
        for (String argument : args) {
            if (argument.startsWith("--frames=")) {
                long value = Long.parseLong(argument.substring("--frames=".length()));
                if (value <= 0) {
                    throw new IllegalArgumentException("--frames must be greater than zero");
                }
                return value;
            }
        }
        return 0;
    }
}
