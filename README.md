# LavaFlow

> [!CAUTION]
> **100% AI 生成的项目**
>
> LavaFlow 的源代码与文档**100% 由 AI 在人类指导下生成**。请将其视为实验性实现：在依赖它之前，先审计代码并在你自己的硬件上测试。

> [!NOTE]
> 本仓库是基于原始项目 [BZLZHH/LavaFlow](https://github.com/BZLZHH/LavaFlow)（NeoForge 版）开发的 Fabric 移植版。Vulkan 后端的设计与实现全部归功于原项目。

LavaFlow 是 Minecraft Blaze3D API 的实验性 Vulkan 1.1 图形后端。它用 LavaFlow 自有的 LWJGL Vulkan 实现替换了实际执行的 Blaze3D 渲染路径，并且从不调用 OpenGL。

## 兼容性

| 组件 | 目标 |
| --- | --- |
| Minecraft | 26.2 |
| 模组加载器 | Fabric Loader 0.19.3 或更高 |
| Minecraft 运行时 | Java 25 |
| 图形 API | Vulkan 1.1 |
| 桌面 smoke 渲染器 | Java 21 字节码 |

后端为不具备以下能力的 Vulkan 1.1 设备提供兼容路径：dynamic rendering、synchronization2、push descriptors、multi-draw indirect、非 solid fill mode、顶点属性除数。桌面端与 ARM64 Android 设备均在范围内。实际驱动行为与性能因 GPU 而异。

## 当前功能

- LavaFlow 自有的 Vulkan 实例、呈现设备、队列与交换链
- Blaze3D 纹理、缓冲、采样器、管线、渲染通道与命令编码
- 动态渲染与旧版渲染通道路径
- push-descriptor 与 descriptor-set 路径
- 显式资源生命周期与队列同步
- Vulkan 原生纹理传输、blit、清除与呈现
- 缩放与交换链重建处理
- 最终呈现 blit 时反转目标 Y 坐标
- Sodium 0.9.1 兼容：Sodium 的 Vulkan 地形路径在 LavaFlow 上运行，而非回退到 OpenGL

LavaFlow 使用顺时针前面，且不启用 shaderc 的 invert-Y 选项。这些约定是为了匹配 Minecraft 官方 Vulkan 后端的行为。

## 架构

```text
Minecraft Blaze3D API
        |
LavaFlow Fabric 适配器（dev.lavaflow.minecraft）
        |
LavaFlow Vulkan 后端（dev.lavaflow.vulkan）
        |
Vulkan 1.1
```

面向 Minecraft 的适配器位于 `dev.lavaflow.minecraft`。`dev.lavaflow.vulkan` 下的独立 Vulkan 渲染器不包含任何 Minecraft 类，从而将 Vulkan 资源所有权与批处理策略与 Minecraft 解耦。

### 源码结构

| 路径 | 职责 |
| --- | --- |
| `src/main/java/dev/lavaflow/vulkan/` | 独立 Vulkan 核心：实例/设备/交换链（`LavaFlowRenderer`、`FrameResources`、`QueueFamilies`、`SwapchainState`、`SwapchainSupport`、`VulkanException`）。无 Minecraft 类。 |
| `src/main/java/dev/lavaflow/smoke/` | `LavaFlowSmoke`，独立的 GLFW + Vulkan 清屏循环，用于在没有 Minecraft 的情况下验证后端。 |
| `src/minecraft/java/dev/lavaflow/minecraft/` | Fabric 适配器。`LavaFlowBackend` 实现 Blaze3D 的 `GpuBackend`；`vulkan/` 子包存放 Blaze3D Vulkan 实现（`LavaFlowDevice`、`LavaFlowGpuBuffer`、`LavaFlowGpuSampler`、`LavaFlowGpuTexture`、`LavaFlowGpuTextureView`、`LavaFlowGpuSurface`、`LavaFlowRenderPass`、`LavaFlowRenderPipeline`、`LavaFlowCommandEncoder`、`LavaFlowDescriptorCache`、`LavaFlowShaderc`、`LavaFlowVulkanPass`、`LavaFlowVulkanContext`、`LavaFlowTransientMemory`、`LavaFlowFence`、`LavaFlowQueryPool`、`LavaFlowFrameStats`、`LavaFlowPushConstants`、`LavaFlowVk`、`LavaFlowVersion` 等）。 |
| `src/minecraft/java/dev/lavaflow/minecraft/mixin/` | 核心 mixin：`PreferredGraphicsApiMixin` 选中 LavaFlow 作为后端；`FramerateLimitMixin` 与 `FrameStatsMixin` 为由系统属性门控的开发期插桩。 |
| `src/minecraft/java/dev/lavaflow/minecraft/sodium/` | Sodium 0.9.1 兼容。`LavaFlowSodium` 向 Sodium 暴露 LavaFlow 的设备/渲染通道；`LavaFlowSodiumMixinPlugin` 为 mixin 插件。`sodium/mixin/` 下：`DrawBackendMixin` 将 Sodium 路由至其 Vulkan 路径；`VKDrawContextMixin` 绑定 LavaFlow 的命令缓冲与管线布局；`GpuDeviceBackendAccessor`、`RenderPassBackendAccessor`、`DrawContextPassAccessor` 读取 Blaze3D 的 backend 字段。 |
| `src/sodiumStub/java/` | Sodium 的编译期签名 stub（`net.caffeinemc.*`）。不打包；运行时由 Sodium 提供真实类。 |
| `src/minecraft/resources/` | `fabric.mod.json` 与 mixin 配置（`lavaflow.mixins.json`、`lavaflow-sodium.mixins.json`）。 |
| `src/minecraft-test/java/`、`src/test/java/` | 单元测试：Vulkan 重映射（`LavaFlowVkTest`、`LavaFlowPushConstantsTest`）与队列族逻辑（`QueueFamiliesTest`）。 |

### Sodium 兼容说明

Sodium 通过检测 Minecraft 自有的 `VulkanDevice` 来选择后端，因此一个外部的 Vulkan 后端原本会被当作 OpenGL，Sodium 会发起 OpenGL 调用。LavaFlow 选中了基于核心 `vkCmdDrawIndexedIndirect` 的 indirect Vulkan 路径（无需任何设备扩展）。

## 构建

前置条件：

- JDK 25
- Vulkan 1.1 loader 与驱动
- Gradle 9.5.1 或兼容版本

Fabric Loom 会在首次构建时自动下载 Minecraft 26.2 客户端。

构建并测试项目：

```sh
gradle --no-daemon clean test jar
```

Fabric 模组产物输出至：

```text
build/libs/lavaflow-0.1.0-alpha.jar
```

GitHub Actions 会对推送、拉取请求与手动触发运行相同的构建，然后将 JAR 作为工作流产物发布。

## smoke 渲染器

运行独立的 Vulkan smoke 渲染器：

```sh
gradle run
```

有限帧的自动化运行：

```sh
gradle run --args='--frames=120'
```

窗口应通过 Vulkan 持续清屏，并在标题中显示所选 GPU 与帧数。关闭窗口时会演练有序的资源回收。

## 在 Fabric 上安装

1. 将 `build/libs/lavaflow-0.1.0-alpha.jar` 复制到 Minecraft 26.2 实例的 `mods` 目录。
2. 选择 Vulkan 作为实例的图形后端。
3. 使用 Java 25 启动 Minecraft。

对于 Android 上的 FCL，典型的模组路径为：

```text
/storage/emulated/0/FCL/.minecraft/versions/26.2-Fabric/mods/
```

具体实例目录可能因启动器配置而异。

## 状态

LavaFlow 是实验性软件。渲染正确性与性能已在有限的桌面与 ARM64 设备上测试，但 Vulkan 驱动差异可能暴露设备特定问题。测试新构建时请保留一个已知可用的 JAR。

## 致谢

- 原项目：[BZLZHH/LavaFlow](https://github.com/BZLZHH/LavaFlow) —— Vulkan 1.1 后端的设计与实现。
- 工具链：[Fabric Loader](https://fabricmc.net/)、[Fabric Loom](https://github.com/FabricMC/fabric-loom)、[LWJGL 3](https://www.lwjgl.org/)。

## 许可证

LavaFlow 依据 [MIT License](LICENSE) 分发。Copyright (c) 2026 BZLZHH。
