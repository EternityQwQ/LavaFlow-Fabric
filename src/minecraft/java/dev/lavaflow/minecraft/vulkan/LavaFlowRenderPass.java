package dev.lavaflow.minecraft.vulkan;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Vector4fc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdBeginRenderingKHR;
import static org.lwjgl.vulkan.KHRPushDescriptor.vkCmdPushDescriptorSetKHR;
import static org.lwjgl.vulkan.VK10.*;

/** Vulkan 1.1 render-pass attachment and draw state. */
final class LavaFlowRenderPass implements RenderPassBackend, LavaFlowVulkanPass {
    private final LavaFlowCommandEncoder encoder;
    private final LavaFlowVulkanContext context;
    private final RenderPassDescriptor descriptor;
    private final int outputWidth;
    private final int outputHeight;
    private final long renderPass;
    private final List<FinalLayout> finalLayouts = new ArrayList<>();
    private final Map<String, GpuBufferSlice> uniforms = new HashMap<>();
    private final Map<String, TextureBinding> textures = new HashMap<>();
    private LavaFlowRenderPipeline pipeline;
    private boolean descriptorsDirty = true;
    private boolean finished;

    private static final class TextureBinding {
        LavaFlowGpuTextureView view;
        LavaFlowGpuSampler sampler;

        TextureBinding(LavaFlowGpuTextureView view, LavaFlowGpuSampler sampler) {
            this.view = view;
            this.sampler = sampler;
        }
    }
    private record FinalLayout(LavaFlowGpuTexture texture, int layout) {}

    LavaFlowRenderPass(LavaFlowCommandEncoder encoder, RenderPassDescriptor descriptor) {
        this.encoder = encoder;
        this.context = encoder.device().context();
        this.descriptor = descriptor;

        List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> colors = descriptor.colorAttachments();
        RenderPassDescriptor.Attachment<OptionalDouble> depth = descriptor.depthAttachment();
        GpuTextureView extentView = null;
        for (RenderPassDescriptor.Attachment<Optional<Vector4fc>> attachment : colors) {
            if (attachment != null) extentView = attachment.textureView();
        }
        if (extentView == null && depth != null) extentView = depth.textureView();
        if (extentView == null) throw new IllegalArgumentException("Render pass has no attachments");
        outputWidth = extentView.getWidth(0);
        outputHeight = extentView.getHeight(0);

        long activeRenderPass;
        try (MemoryStack stack = stackPush()) {
            LavaFlowGpuTextureView[] colorViews = new LavaFlowGpuTextureView[colors.size()];
            int[] colorFormats = new int[colors.size()];
            int[] colorLoadOps = new int[colors.size()];
            for (int i = 0; i < colors.size(); i++) {
                RenderPassDescriptor.Attachment<Optional<Vector4fc>> attachment = colors.get(i);
                if (attachment == null) {
                    colorFormats[i] = VK_FORMAT_UNDEFINED;
                    colorLoadOps[i] = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
                    continue;
                }
                LavaFlowGpuTextureView view = view(attachment.textureView());
                colorViews[i] = view;
                colorFormats[i] = LavaFlowVk.format(view.texture().getFormat());
                colorLoadOps[i] = attachment.clearValue().isPresent()
                        ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD;
                prepareAttachment(view.texture(), VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            }
            LavaFlowGpuTextureView depthView = depth == null ? null : view(depth.textureView());
            int depthFormat = depthView == null ? VK_FORMAT_UNDEFINED
                    : LavaFlowVk.format(depthView.texture().getFormat());
            int depthLoadOp = depth == null || depth.clearValue().isEmpty()
                    ? VK_ATTACHMENT_LOAD_OP_LOAD : VK_ATTACHMENT_LOAD_OP_CLEAR;
            if (depthView != null) {
                prepareAttachment(depthView.texture(), VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            }

            if (context.dynamicRendering()) {
                beginDynamic(stack, colors, colorViews, depth, depthView);
                activeRenderPass = 0;
            } else {
                activeRenderPass = context.legacyRenderPass(colorFormats, colorLoadOps, depthFormat, depthLoadOp);
                beginLegacy(stack, activeRenderPass, colors, colorViews, depth, depthView);
            }
            RenderPass.RenderArea area = descriptor.renderArea;
            setViewport(stack);
            setScissor(stack, area.x(), area.y(), area.width(), area.height());
        }
        renderPass = activeRenderPass;
    }

    private void prepareAttachment(LavaFlowGpuTexture texture, int attachmentLayout) {
        encoder.transition(texture, attachmentLayout);
        int finalLayout = (texture.usage() & GpuTexture.USAGE_TEXTURE_BINDING) != 0
                ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : attachmentLayout;
        if (!hasFinalLayout(texture)) finalLayouts.add(new FinalLayout(texture, finalLayout));
    }

    private void beginDynamic(MemoryStack stack,
                              List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> colors,
                              LavaFlowGpuTextureView[] colorViews,
                              RenderPassDescriptor.Attachment<OptionalDouble> depth,
                              LavaFlowGpuTextureView depthView) {
        VkRenderingAttachmentInfo.Buffer colorAttachments = VkRenderingAttachmentInfo.calloc(colors.size(), stack);
        for (int i = 0; i < colors.size(); i++) {
            RenderPassDescriptor.Attachment<Optional<Vector4fc>> attachment = colors.get(i);
            VkRenderingAttachmentInfo renderingAttachment = colorAttachments.get(i).sType$Default();
            if (attachment == null) {
                renderingAttachment.imageView(0).imageLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
                continue;
            }
            boolean clear = attachment.clearValue().isPresent();
            renderingAttachment.imageView(colorViews[i].handle())
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(clear ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            if (clear) {
                Vector4fc value = attachment.clearValue().get();
                renderingAttachment.clearValue().color()
                        .float32(stack.floats(value.x(), value.y(), value.z(), value.w()));
            }
        }
        VkRenderingAttachmentInfo depthAttachment = null;
        if (depthView != null) {
            boolean clear = depth.clearValue().isPresent();
            depthAttachment = VkRenderingAttachmentInfo.calloc(stack).sType$Default()
                    .imageView(depthView.handle()).imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .loadOp(clear ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            if (clear) depthAttachment.clearValue().depthStencil()
                    .depth((float) depth.clearValue().getAsDouble()).stencil(0);
        }
        RenderPass.RenderArea area = descriptor.renderArea;
        VkRenderingInfo rendering = VkRenderingInfo.calloc(stack).sType$Default()
                .layerCount(1).viewMask(0).pColorAttachments(colorAttachments);
        rendering.renderArea().offset().set(area.x(), area.y());
        rendering.renderArea().extent().set(area.width(), area.height());
        if (depthAttachment != null) rendering.pDepthAttachment(depthAttachment);
        vkCmdBeginRenderingKHR(encoder.commandBuffer(), rendering);
    }

    private void beginLegacy(MemoryStack stack, long renderPass,
                             List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> colors,
                             LavaFlowGpuTextureView[] colorViews,
                             RenderPassDescriptor.Attachment<OptionalDouble> depth,
                             LavaFlowGpuTextureView depthView) {
        int attachmentCount = depthView == null ? 0 : 1;
        for (LavaFlowGpuTextureView view : colorViews) if (view != null) attachmentCount++;
        long[] views = new long[attachmentCount];
        VkClearValue.Buffer clearValues = VkClearValue.calloc(attachmentCount, stack);
        int attachmentIndex = 0;
        for (int i = 0; i < colorViews.length; i++) {
            LavaFlowGpuTextureView view = colorViews[i];
            if (view == null) continue;
            views[attachmentIndex] = view.handle();
            Optional<Vector4fc> clear = colors.get(i).clearValue();
            if (clear.isPresent()) {
                Vector4fc value = clear.get();
                clearValues.get(attachmentIndex).color()
                        .float32(stack.floats(value.x(), value.y(), value.z(), value.w()));
            }
            attachmentIndex++;
        }
        if (depthView != null) {
            views[attachmentIndex] = depthView.handle();
            if (depth.clearValue().isPresent()) clearValues.get(attachmentIndex).depthStencil()
                    .depth((float) depth.clearValue().getAsDouble()).stencil(0);
        }
        long framebuffer = context.legacyFramebuffer(renderPass, views, outputWidth, outputHeight);
        RenderPass.RenderArea area = descriptor.renderArea;
        VkRenderPassBeginInfo begin = VkRenderPassBeginInfo.calloc(stack).sType$Default()
                .renderPass(renderPass).framebuffer(framebuffer).pClearValues(clearValues);
        begin.renderArea().offset().set(area.x(), area.y());
        begin.renderArea().extent().set(area.width(), area.height());
        vkCmdBeginRenderPass(encoder.commandBuffer(), begin, VK_SUBPASS_CONTENTS_INLINE);
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) throw new IllegalStateException(operation + " failed with VkResult " + result);
    }

    private void setViewport(MemoryStack stack) {
        VkViewport.Buffer viewport = VkViewport.calloc(1, stack).x(0).y(0)
                .width(outputWidth).height(outputHeight).minDepth(0).maxDepth(1);
        vkCmdSetViewport(encoder.commandBuffer(), 0, viewport);
    }

    private void setScissor(MemoryStack stack, int x, int y, int width, int height) {
        VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
        scissor.offset().set(x, y);
        scissor.extent().set(width, height);
        vkCmdSetScissor(encoder.commandBuffer(), 0, scissor);
    }

    void finish() {
        if (finished) return;
        finished = true;
        for (FinalLayout finalLayout : finalLayouts) {
            encoder.transition(finalLayout.texture(), finalLayout.layout());
        }
    }

    private boolean hasFinalLayout(LavaFlowGpuTexture texture) {
        for (int i = 0; i < finalLayouts.size(); i++) {
            if (finalLayouts.get(i).texture() == texture) return true;
        }
        return false;
    }

    boolean hasDepth() { return descriptor.depthAttachment() != null; }

    @Override public VkCommandBuffer lavaflowCommandBuffer() { return encoder.commandBuffer(); }
    @Override public long lavaflowPipelineLayout() { return pipeline == null ? 0L : pipeline.pipelineLayout(); }

    @Override public void pushDebugGroup(Supplier<String> label) {}
    @Override public void popDebugGroup() {}
    @Override public void setPipeline(RenderPipeline pipeline) {
        this.pipeline = encoder.device().pipeline(pipeline);
        if (!this.pipeline.isValid()) throw new IllegalStateException("Pipeline is invalid: " + pipeline.getLocation());
        vkCmdBindPipeline(encoder.commandBuffer(), VK_PIPELINE_BIND_POINT_GRAPHICS,
                this.pipeline.pipelineFor(hasDepth(), renderPass));
        descriptorsDirty = true;
    }
    @Override public void bindTexture(String name, GpuTextureView texture, GpuSampler sampler) {
        if ((texture == null) != (sampler == null)) throw new IllegalArgumentException("Texture and sampler must both be null or non-null");
        if (texture == null) {
            textures.remove(name);
        } else {
            textures.put(name, new TextureBinding(view(texture), (LavaFlowGpuSampler)sampler));
        }
        descriptorsDirty = true;
    }
    @Override public void setUniform(String name, GpuBuffer buffer) {
        setUniform(name, buffer.slice());
    }
    @Override public void setUniform(String name, GpuBufferSlice buffer) {
        uniforms.put(name, buffer);
        descriptorsDirty = true;
    }
    @Override public void enableScissor(int x, int y, int width, int height) {
        try (MemoryStack stack = stackPush()) { setScissor(stack, x, y, width, height); }
    }
    @Override public void disableScissor() {
        RenderPass.RenderArea area = descriptor.renderArea;
        enableScissor(area.x(), area.y(), area.width(), area.height());
    }
    @Override public void setVertexBuffer(int slot, GpuBufferSlice buffer) {
        long handle = buffer == null ? 0 : ((LavaFlowGpuBuffer) buffer.buffer()).handle();
        long offset = buffer == null ? 0 : buffer.offset();
        try (MemoryStack stack = stackPush()) {
            vkCmdBindVertexBuffers(encoder.commandBuffer(), slot, stack.longs(handle), stack.longs(offset));
        }
    }
    @Override public void setIndexBuffer(GpuBuffer buffer, IndexType type) {
        long handle = ((LavaFlowGpuBuffer) buffer).handle();
        int vkType = type == IndexType.SHORT ? VK_INDEX_TYPE_UINT16 : VK_INDEX_TYPE_UINT32;
        vkCmdBindIndexBuffer(encoder.commandBuffer(), handle, 0, vkType);
    }
    @Override public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance) {
        pushDescriptors();
        vkCmdDrawIndexed(encoder.commandBuffer(), indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }
    @Override public void multiDrawIndexed(IntBuffer counts, int indexType, int firstInstance, int instanceCount) { unsupported(); }
    @Override public void multiDrawIndexed(PointerBuffer buffers, IntBuffer counts, IntBuffer baseVertices, int instanceCount) { unsupported(); }
    @Override public void drawIndexedIndirect(GpuBufferSlice buffer, int count) {
        pushDescriptors();
        long handle = ((LavaFlowGpuBuffer) buffer.buffer()).handle();
        if (count <= 1 || context.multiDrawIndirect()) {
            vkCmdDrawIndexedIndirect(encoder.commandBuffer(), handle, buffer.offset(), count,
                    VkDrawIndexedIndirectCommand.SIZEOF);
        } else {
            for (int i = 0; i < count; i++) {
                vkCmdDrawIndexedIndirect(encoder.commandBuffer(), handle,
                        buffer.offset() + (long) i * VkDrawIndexedIndirectCommand.SIZEOF,
                        1, VkDrawIndexedIndirectCommand.SIZEOF);
            }
        }
    }
    @Override public <T> void drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, GpuBuffer buffer, IndexType type, Collection<String> uniformNames, T value) {
        for (RenderPass.Draw<T> draw : draws) {
            if (draw.uniformUploaderConsumer() != null) {
                draw.uniformUploaderConsumer().accept(value, this::setUniform);
            }
            setIndexBuffer(draw.indexBuffer() == null ? buffer : draw.indexBuffer(),
                    draw.indexType() == null ? type : draw.indexType());
            setVertexBuffer(draw.slot(), draw.vertexBuffer().slice());
            drawIndexed(draw.indexCount(), 1, draw.firstIndex(), draw.baseVertex(), 0);
        }
    }
    @Override public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        pushDescriptors();
        vkCmdDraw(encoder.commandBuffer(), vertexCount, instanceCount, firstVertex, firstInstance);
    }
    @Override public void multiDraw(IntBuffer counts, int firstInstance, int instanceCount, int firstVertex) { unsupported(); }
    @Override public void multiDraw(IntBuffer counts, IntBuffer firstVertices, int instanceCount) { unsupported(); }
    @Override public void drawIndirect(GpuBufferSlice buffer, int count) {
        pushDescriptors();
        long handle = ((LavaFlowGpuBuffer) buffer.buffer()).handle();
        if (count <= 1 || context.multiDrawIndirect()) {
            vkCmdDrawIndirect(encoder.commandBuffer(), handle, buffer.offset(), count, VkDrawIndirectCommand.SIZEOF);
        } else {
            for (int i = 0; i < count; i++) {
                vkCmdDrawIndirect(encoder.commandBuffer(), handle,
                        buffer.offset() + (long) i * VkDrawIndirectCommand.SIZEOF,
                        1, VkDrawIndirectCommand.SIZEOF);
            }
        }
    }
    @Override public void writeTimestamp(GpuQueryPool pool, int index) { encoder.writeTimestamp(pool, index); }

    private static LavaFlowGpuTextureView view(GpuTextureView view) { return (LavaFlowGpuTextureView) view; }

    private void pushDescriptors() {
        if (pipeline == null) throw new IllegalStateException("No graphics pipeline is bound");
        if (!descriptorsDirty) return;
        List<LavaFlowRenderPipeline.Entry> entries = pipeline.entries();
        if (entries.isEmpty()) {
            descriptorsDirty = false;
            return;
        }
        try (MemoryStack stack = stackPush()) {
            long descriptorSet = context.pushDescriptors() ? 0
                    : encoder.allocateDescriptorSet(pipeline.descriptorSetLayout());
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(entries.size(), stack);
            for (int i = 0; i < entries.size(); i++) {
                LavaFlowRenderPipeline.Entry entry = entries.get(i);
                VkWriteDescriptorSet write = writes.get(i).sType$Default().dstBinding(i)
                        .dstArrayElement(0).descriptorCount(1)
                        .descriptorType(LavaFlowRenderPipeline.descriptorType(entry.type()));
                if (descriptorSet != 0) write.dstSet(descriptorSet);
                switch (entry.type()) {
                    case UNIFORM_BUFFER -> {
                        GpuBufferSlice slice = requireUniform(entry.name());
                        VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                                .buffer(((LavaFlowGpuBuffer) slice.buffer()).handle()).offset(slice.offset()).range(slice.length());
                        write.pBufferInfo(bufferInfo);
                    }
                    case SAMPLED_IMAGE -> {
                        TextureBinding binding = textures.get(entry.name());
                        if (binding == null) throw new IllegalStateException("Missing sampled image " + entry.name());
                        LavaFlowGpuTexture sampledTexture = binding.view.texture();
                        if (sampledTexture.layout() != VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                            throw new IllegalStateException("Sampled image " + entry.name() + " is not shader-readable (layout " + sampledTexture.layout() + ")");
                        }
                        VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                                .sampler(binding.sampler.handle()).imageView(binding.view.handle())
                                .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                        write.pImageInfo(imageInfo);
                    }
                    case TEXEL_BUFFER -> {
                        GpuBufferSlice slice = requireUniform(entry.name());
                        VkBufferViewCreateInfo viewInfo = VkBufferViewCreateInfo.calloc(stack).sType$Default()
                                .buffer(((LavaFlowGpuBuffer) slice.buffer()).handle()).format(LavaFlowVk.format(entry.texelFormat()))
                                .offset(slice.offset()).range(slice.length());
                        LongBuffer viewOut = stack.mallocLong(1);
                        check(vkCreateBufferView(context.device(), viewInfo, null, viewOut), "vkCreateBufferView");
                        long bufferView = viewOut.get(0);
                        encoder.device().defer(() -> vkDestroyBufferView(context.device(), bufferView, null));
                        write.pTexelBufferView(stack.longs(bufferView));
                    }
                }
            }
            if (context.pushDescriptors()) {
                vkCmdPushDescriptorSetKHR(encoder.commandBuffer(), VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipeline.pipelineLayout(), 0, writes);
            } else {
                vkUpdateDescriptorSets(context.device(), writes, null);
                vkCmdBindDescriptorSets(encoder.commandBuffer(), VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipeline.pipelineLayout(), 0, stack.longs(descriptorSet), null);
            }
        }
        descriptorsDirty = false;
    }

    private GpuBufferSlice requireUniform(String name) {
        GpuBufferSlice slice = uniforms.get(name);
        if (slice == null) throw new IllegalStateException("Missing uniform " + name);
        if (slice.buffer().isClosed()) throw new IllegalStateException("Uniform buffer is closed: " + name);
        return slice;
    }

    private static void unsupported() { throw new UnsupportedOperationException("LavaFlow graphics pipeline binding is not initialized"); }
}
