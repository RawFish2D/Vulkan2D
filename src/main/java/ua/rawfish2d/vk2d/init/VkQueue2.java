package ua.rawfish2d.vk2d.init;

import lombok.Getter;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkQueue;
import ua.rawfish2d.vk2d.vkutils.VkHelper;

@Getter
public class VkQueue2 {
	private final VkDevice vkLogicalDevice;
	private final VkQueue queue;
	private final VkDeviceInstance.VkQueueInfo queueInfo;
	private long vkCommandPool;
	private VkCommandBuffer vkCommandBuffer;

	public VkQueue2(VkDevice vkLogicalDevice, VkQueue queue, VkDeviceInstance.VkQueueInfo queueInfo) {
		this.vkLogicalDevice = vkLogicalDevice;
		this.queue = queue;
		this.queueInfo = queueInfo;
	}

	public void createCommandPool() {
		vkCommandPool = VkHelper.createCommandPool(vkLogicalDevice, queueInfo.queueIndex());
	}

	public void createCommandBuffer() {
		vkCommandBuffer = VkHelper.createCommandBuffer(vkLogicalDevice, vkCommandPool);
	}
}
