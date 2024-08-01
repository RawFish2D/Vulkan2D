package ua.rawfish2d.vk2d;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

@Getter
public class Framebuffer {
	private final List<VkBuffer> uniformBuffers = new ArrayList<>();
	private long vkSwapChainFramebuffer;
	private long vkImageAvailableSemaphore;
	private long vkRenderFinishedSemaphore;
	private long vkSwapChainImage;
	private long vkSwapChainImageView;
	private long vkInFlightFence;
	//
	@Setter
	private VkCommandBuffer vkCommandBuffer;

	public void createFramebuffer(VkDevice vkLogicalDevice, VkExtent2D vkExtent2D, long vkRenderPass, long vkSwapChainImage, long vkSwapChainImageView) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final LongBuffer pAttachments = stack.mallocLong(1);
			pAttachments.put(0, vkSwapChainImageView);

			final VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
					.sType$Default()
					.renderPass(vkRenderPass)
					.attachmentCount(1)
					.pAttachments(pAttachments)
					.width(vkExtent2D.width())
					.height(vkExtent2D.height())
					.layers(1);

			final LongBuffer pSwapChainFramebuffer = stack.mallocLong(1);
			if (vkCreateFramebuffer(vkLogicalDevice, framebufferInfo, null, pSwapChainFramebuffer) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create framebuffer!");
			}

			this.vkSwapChainFramebuffer = pSwapChainFramebuffer.get(0);
			this.vkSwapChainImage = vkSwapChainImage;
			this.vkSwapChainImageView = vkSwapChainImageView;
		}
	}

	public void addUniformBuffer(VkBuffer uniformBuffer) {
		this.uniformBuffers.add(uniformBuffer);
	}

	public void createSyncObjects(VkDevice vkLogicalDevice) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkSemaphoreCreateInfo semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack)
					.sType$Default();

			final VkFenceCreateInfo fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
					.sType$Default()
					.flags(VK_FENCE_CREATE_SIGNALED_BIT); // this is important
			// without VK_FENCE_CREATE_SIGNALED_BIT flag we will wait for finish drawing previous frame
			// but because this is the first frame, it will just freeze forever, this flag fixes it

			final LongBuffer pImageAvailableSemaphore = stack.mallocLong(1);
			final LongBuffer pRenderFinishedSemaphore = stack.mallocLong(1);
			final LongBuffer pInFlightFence = stack.mallocLong(1);
			if (vkCreateSemaphore(vkLogicalDevice, semaphoreCreateInfo, null, pImageAvailableSemaphore) != VK_SUCCESS ||
					vkCreateSemaphore(vkLogicalDevice, semaphoreCreateInfo, null, pRenderFinishedSemaphore) != VK_SUCCESS ||
					vkCreateFence(vkLogicalDevice, fenceCreateInfo, null, pInFlightFence) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create semaphores!");
			}
			vkImageAvailableSemaphore = pImageAvailableSemaphore.get(0);
			vkRenderFinishedSemaphore = pRenderFinishedSemaphore.get(0);
			vkInFlightFence = pInFlightFence.get(0);
		}
	}

	public void destroy(VkDevice vkLogicalDevice) {
		vkDestroySemaphore(vkLogicalDevice, vkImageAvailableSemaphore, null);
		vkDestroySemaphore(vkLogicalDevice, vkRenderFinishedSemaphore, null);
		vkDestroyFence(vkLogicalDevice, vkInFlightFence, null);

//		vkDestroyCommandPool(vkLogicalDevice, vkCommandPool, null);

		vkDestroyFramebuffer(vkLogicalDevice, vkSwapChainFramebuffer, null);
	}

	public void onRecreateSwapChain_1(VkDevice vkLogicalDevice) {
		vkDestroyFramebuffer(vkLogicalDevice, vkSwapChainFramebuffer, null);
		vkDestroyImageView(vkLogicalDevice, vkSwapChainImageView, null);
	}

	public void onRecreateSwapChain_2(VkDevice vkLogicalDevice, VkExtent2D vkExtent2D, long vkRenderPass, long vkSwapchainKHR, long vkSwapChainImage, long vkSwapChainImageView) {
		createFramebuffer(vkLogicalDevice, vkExtent2D, vkRenderPass, vkSwapChainImage, vkSwapChainImageView);
	}
}
