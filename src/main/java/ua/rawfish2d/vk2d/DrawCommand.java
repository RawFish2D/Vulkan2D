package ua.rawfish2d.vk2d;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import ua.rawfish2d.BulletTest;
import ua.rawfish2d.vk2d.init.VkDeviceInstance;
import ua.rawfish2d.vk2d.vkutils.VkHelper;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdBeginRenderingKHR;
import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdEndRenderingKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class DrawCommand {
	public final VkSubmitInfo submitInfo;
	public final VkSubmitInfo submitUploadInfo;
	private final VkDevice vkLogicalDevice;
	private final VkGraphicsPipeline vkGraphicsPipeline;
	private final VkExtent2D vkExtent2D;
	private final VkBuffer uniformBuffer;
	@Getter
	private final Framebuffer framebuffer;
	private final long vkRenderPass;
	private final VkBuffer vkVertexBuffer;
	private final VkBuffer vkIndexBuffer;
	private final IndirectBuffer vkIndirectBuffer;
	private final Descriptor descriptor;
	private final int bulletsCount;
	@Setter
	private int windowWidth;
	@Setter
	private int windowHeight;
	private final AtomicBoolean canBeSubmitted = new AtomicBoolean();
	// drawing
	@Setter
	@Getter
	private boolean drawCommandCached = false;
	private final long vkCommandPool;
	@Getter
	private VkCommandBuffer vkDrawCommandBuffer;
	// uploading
	@Setter
	@Getter
	private boolean drawAndUploadCommandCached = false;
	@Getter
	private VkCommandBuffer vkDrawAndUploadCommandBuffer;
	private long vkUploadFinishedSemaphore;

	public DrawCommand(VkDeviceInstance deviceInstance, long vkRenderPass, VkExtent2D vkExtent2D, VkGraphicsPipeline vkGraphicsPipeline, VkBuffer vkVertexBuffer, VkBuffer vkIndexBuffer, IndirectBuffer vkIndirectBuffer, Descriptor descriptor, int bulletsCount, VkBuffer uniformBuffer, Framebuffer framebuffer) {
		submitInfo = VkSubmitInfo.calloc()
				.pWaitSemaphores(memAllocLong(1))
				.pWaitDstStageMask(memAllocInt(1))
				.pCommandBuffers(memAllocPointer(1))
				.pSignalSemaphores(memAllocLong(1));
		submitUploadInfo = VkSubmitInfo.calloc()
				.pWaitSemaphores(memAllocLong(1))
				.pWaitDstStageMask(memAllocInt(1))
				.pCommandBuffers(memAllocPointer(1))
				.pSignalSemaphores(memAllocLong(1));

		this.vkLogicalDevice = deviceInstance.getVkLogicalDevice();
		this.uniformBuffer = uniformBuffer;
		this.framebuffer = framebuffer;

		this.vkRenderPass = vkRenderPass;
		this.vkExtent2D = vkExtent2D;
		this.vkGraphicsPipeline = vkGraphicsPipeline;
		this.vkVertexBuffer = vkVertexBuffer;
		this.vkIndexBuffer = vkIndexBuffer;
		this.vkIndirectBuffer = vkIndirectBuffer;
		this.descriptor = descriptor;
		this.bulletsCount = bulletsCount;

		int graphicsQueueIndex = deviceInstance.getQueue(VK_QUEUE_GRAPHICS_BIT, false).getQueueInfo().queueIndex();
		this.vkCommandPool = VkHelper.createCommandPool(vkLogicalDevice, graphicsQueueIndex);
		this.vkDrawCommandBuffer = VkHelper.createCommandBuffer(vkLogicalDevice, vkCommandPool);
		this.vkDrawAndUploadCommandBuffer = VkHelper.createCommandBuffer(vkLogicalDevice, vkCommandPool);
		createSyncObjects(vkLogicalDevice);
	}

	public void recordDrawCommands(int imageIndex, VkCommandBuffer commandBuffer, LongBuffer vkSwapChainImages, long[] vkSwapChainImageViews, BulletTest bulletTest) {
		recordCommandBuffer(commandBuffer, imageIndex, vkSwapChainImages, vkSwapChainImageViews, bulletTest);
	}

	public VkSubmitInfo getDrawSubmitInfo(VkCommandBuffer commandBuffer) {
		submitInfo.sType$Default();
		submitInfo.waitSemaphoreCount(1);
		submitInfo.pWaitSemaphores().put(0, framebuffer.getVkImageAvailableSemaphore());
		submitInfo.pWaitDstStageMask().put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
		submitInfo.pCommandBuffers().put(0, commandBuffer);
		submitInfo.pSignalSemaphores().put(0, framebuffer.getVkRenderFinishedSemaphore());
		return submitInfo;
	}

	public VkSubmitInfo getUploadDrawSubmitInfo(VkCommandBuffer commandBuffer) {
		submitUploadInfo.sType$Default();
		submitUploadInfo.waitSemaphoreCount(1);
		submitUploadInfo.pWaitSemaphores().put(0, framebuffer.getVkImageAvailableSemaphore());
		submitUploadInfo.pWaitDstStageMask().put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
		submitUploadInfo.pCommandBuffers().put(0, commandBuffer);
		submitUploadInfo.pSignalSemaphores().put(0, framebuffer.getVkRenderFinishedSemaphore());
		return submitUploadInfo;
	}

	private void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex, LongBuffer vkSwapChainImages, long[] vkSwapChainImageViews, BulletTest bulletTest) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			if (bulletTest != null) {
				bulletTest.uploadBulletPos(stack, commandBuffer);
				final ByteBuffer buffer = uniformBuffer.getStagingBuffer();
				buffer.putFloat(0, windowWidth);
				buffer.putFloat(4, windowHeight);
				uniformBuffer.uploadFromStagingBuffer(stack, commandBuffer);
			}

			final VkClearValue vkClearValue = VkHelper.vkGetClearValue(stack, 0, 0, 0, 255);
			if (VK2D.useDynamicRendering) {
				final VkImageMemoryBarrier.Buffer vkImageMemoryBarriers = VkImageMemoryBarrier.calloc(1, stack);
				final VkImageMemoryBarrier vkImageMemoryBarrier = VkImageMemoryBarrier.calloc(stack)
						.sType$Default()
						.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED) // new
						.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED) // new
						.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
						.oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
						.newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
						.image(vkSwapChainImages.get(imageIndex));
				vkImageMemoryBarrier.subresourceRange()
						.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
						.baseMipLevel(0)
						.levelCount(1)
						.baseArrayLayer(0)
						.layerCount(1);
				vkImageMemoryBarriers.put(0, vkImageMemoryBarrier);

				vkCmdPipelineBarrier(
						commandBuffer,
						VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, // srcStageMask
						VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, // dstStageMask
						0,
						null,
						null,
						vkImageMemoryBarriers);

				final VkRenderingAttachmentInfo.Buffer vkRenderingAttachmentInfoBuffer = VkRenderingAttachmentInfoKHR.calloc(1, stack);
				vkRenderingAttachmentInfoBuffer.put(0, VkRenderingAttachmentInfo.calloc(stack)
						.sType$Default()
						.imageView(vkSwapChainImageViews[imageIndex])
						.imageLayout(VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR)
						.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR) // clear framebuffer before using it
						.storeOp(VK_ATTACHMENT_STORE_OP_STORE)
						.clearValue(vkClearValue));

				final VkRenderingInfoKHR vkRenderingInfoKHR = VkRenderingInfoKHR.calloc(stack)
						.sType$Default()
						.layerCount(1)
						.pColorAttachments(vkRenderingAttachmentInfoBuffer);
				vkRenderingInfoKHR.renderArea().offset().set(0, 0);
				vkRenderingInfoKHR.renderArea().extent(vkExtent2D);

				vkCmdBeginRenderingKHR(commandBuffer, vkRenderingInfoKHR);
			} else {
				final VkClearValue.Buffer vkClearValueBuffer = VkClearValue.calloc(1, stack);
				// XNA background color
				// clearColor.put(0, VkHelper.vkGetClearValue(stack, 100, 150, 238, 255));
				vkClearValueBuffer.put(0, vkClearValue);

				final VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
						.sType$Default()
						.renderPass(vkRenderPass)
						.framebuffer(framebuffer.getVkSwapChainFramebuffer())
						.clearValueCount(1)
						.pClearValues(vkClearValueBuffer);
				renderPassInfo.renderArea().offset().set(0, 0);
				renderPassInfo.renderArea().extent(vkExtent2D);

				vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
			}

			vkGraphicsPipeline.bindPipeline(commandBuffer);

			final VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
			viewport.x(0.0f).y(0.0f);
			viewport.width(vkExtent2D.width()).height(vkExtent2D.height());
			viewport.minDepth(0f).maxDepth(1f);
			vkCmdSetViewport(commandBuffer, 0, viewport);

			final VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
			scissor.offset().set(0, 0);
			scissor.extent(vkExtent2D);
			vkCmdSetScissor(commandBuffer, 0, scissor);

			vkVertexBuffer.bindVertexBuffer(commandBuffer);
			vkIndexBuffer.bindIndexBuffer(commandBuffer, VK_INDEX_TYPE_UINT32);

			final LongBuffer pDescriptorSet = stack.longs(descriptor.getDescriptorSet(imageIndex));
			vkCmdBindDescriptorSets(commandBuffer,
					VK_PIPELINE_BIND_POINT_GRAPHICS,
					vkGraphicsPipeline.getVkPipelineLayout(), 0, pDescriptorSet, null);

//			VkBuffer.gigaBarrier(stack, commandBuffer); // validation layers are angry that I didn't enable some feature

			vkCmdDrawIndexedIndirect(commandBuffer, vkIndirectBuffer.getBufferHandle(), 0, 10, 5 * 4);

			if (VK2D.useDynamicRendering) {
				vkCmdEndRenderingKHR(commandBuffer);

				final VkImageMemoryBarrier.Buffer vkImageMemoryBarriers = VkImageMemoryBarrier.calloc(1, stack);
				final VkImageMemoryBarrier vkImageMemoryBarrier = VkImageMemoryBarrier.calloc()
						.sType$Default()
						.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED) // new
						.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED) // new
						.srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
						.oldLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
						.newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
						.image(vkSwapChainImages.get(imageIndex));
				vkImageMemoryBarrier.subresourceRange()
						.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
						.baseMipLevel(0)
						.levelCount(1)
						.baseArrayLayer(0)
						.layerCount(1);
				vkImageMemoryBarriers.put(0, vkImageMemoryBarrier);

				vkCmdPipelineBarrier(
						commandBuffer,
						VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,  // srcStageMask
						VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, // dstStageMask
						0,
						null,
						null,
						vkImageMemoryBarriers);
			} else {
				vkCmdEndRenderPass(commandBuffer);
			}

			VkBuffer.gigaBarrier(stack, commandBuffer); // fixes half of validation errors
		}
	}

	public void createSyncObjects(VkDevice vkLogicalDevice) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkSemaphoreCreateInfo semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack)
					.sType$Default();

			final LongBuffer pUploadFinishedSemaphore = stack.mallocLong(1);
			if (vkCreateSemaphore(vkLogicalDevice, semaphoreCreateInfo, null, pUploadFinishedSemaphore) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create semaphores!");
			}
			this.vkUploadFinishedSemaphore = pUploadFinishedSemaphore.get(0);
		}
	}

	public void free() {
		memFree(submitInfo.pWaitSemaphores());
		memFree(submitInfo.pWaitDstStageMask());
		memFree(submitInfo.pCommandBuffers());
		memFree(submitInfo.pSignalSemaphores());
		submitInfo.free();

		memFree(submitUploadInfo.pWaitSemaphores());
		memFree(submitUploadInfo.pWaitDstStageMask());
		memFree(submitUploadInfo.pCommandBuffers());
		memFree(submitUploadInfo.pSignalSemaphores());
		submitUploadInfo.free();

		vkDestroyCommandPool(vkLogicalDevice, vkCommandPool, null);

		vkDestroySemaphore(vkLogicalDevice, vkUploadFinishedSemaphore, null);
	}

	public boolean canBeSubmitted() {
		return canBeSubmitted.get();
	}

	public void onWasSubmitted() {
		canBeSubmitted.set(false);
	}
}
