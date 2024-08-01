package ua.rawfish2d.vk2d;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import ua.rawfish2d.vk2d.vkutils.VkHelper;

import java.nio.LongBuffer;
import java.util.List;

import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.*;

public class Descriptor {
	private long vkDescriptorSetLayout;
	private long vkDescriptorPool;
	private LongBuffer vkDescriptorSets;

	public void createDescriptorSetLayout(DescriptorSetLayout descriptorSetLayout, VkDevice vkLogicalDevice) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkDescriptorSetLayoutBinding.Buffer descriptorSetLayoutBindings = descriptorSetLayout.makeSetLayoutBinding(stack);

			final VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
					.sType$Default()
					.pBindings(descriptorSetLayoutBindings);

			final LongBuffer pDescriptorSetLayout = stack.mallocLong(1);
			if (vkCreateDescriptorSetLayout(vkLogicalDevice, layoutInfo, null, pDescriptorSetLayout) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create descriptor set layout!");
			}
			vkDescriptorSetLayout = pDescriptorSetLayout.get(0);
		}
	}

	public void createDescriptorPool(DescriptorSetLayout descriptorSetLayout, VkDevice vkLogicalDevice, int swapChainImageCount) {
		final int descriptorPoolSize = descriptorSetLayout.calculateDescriptorPoolSize(swapChainImageCount);
		System.out.println("🔷 pool size: " + descriptorPoolSize);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkDescriptorPoolSize.Buffer vkDescriptorPoolSizes = descriptorSetLayout.makeDescriptorPoolSizes(stack, descriptorPoolSize);

			final VkDescriptorPoolCreateInfo poolCreateInfo = VkDescriptorPoolCreateInfo.calloc(stack)
					.sType$Default()
					.pPoolSizes(vkDescriptorPoolSizes)
					.maxSets(descriptorPoolSize);

			final LongBuffer pDescriptorPool = stack.mallocLong(1);
			if (vkCreateDescriptorPool(vkLogicalDevice, poolCreateInfo, null, pDescriptorPool) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create descriptor pool!");
			}
			vkDescriptorPool = pDescriptorPool.get(0);
		}
	}

	public void createDescriptorSets(DescriptorSetLayout descriptorSetLayout, VkDevice vkLogicalDevice, final int descriptorSetLayoutsCount, List<DescriptorSetLayout.UpdateDescriptorSetInfo> updateDescriptorSetInfoList) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final LongBuffer pSetLayouts = stack.mallocLong(descriptorSetLayoutsCount);
			for (int a = 0; a < descriptorSetLayoutsCount; ++a) {
				pSetLayouts.put(a, vkDescriptorSetLayout);
			}
			final VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
					.sType$Default()
					.descriptorPool(vkDescriptorPool)
					.pSetLayouts(pSetLayouts);

			if (vkDescriptorSets != null) {
				memFree(vkDescriptorSets);
			}
			vkDescriptorSets = MemoryUtil.memAllocLong(descriptorSetLayoutsCount);
			int result = vkAllocateDescriptorSets(vkLogicalDevice, allocInfo, vkDescriptorSets);
			if (result != VK_SUCCESS) {
				throw new RuntimeException("Failed to allocate descriptor sets! Error: " + VkHelper.translateVulkanResult(result));
			}
		}

		for (DescriptorSetLayout.UpdateDescriptorSetInfo updateDescriptorSetInfo : updateDescriptorSetInfoList) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				final long vkDescriptorSet = vkDescriptorSets.get(updateDescriptorSetInfo.swapChainIndex());
				final VkWriteDescriptorSet.Buffer descriptorWrite = descriptorSetLayout.makeWriteDescriptorSet(vkDescriptorSet, updateDescriptorSetInfo.bufferLayout(), updateDescriptorSetInfo.vkBuffer(), stack);
				vkUpdateDescriptorSets(vkLogicalDevice, descriptorWrite, null);
			}
		}

//		for (int a = 0; a < descriptorSetLayoutsCount; ++a) {
//			try (MemoryStack stack = MemoryStack.stackPush()) {
////				final VkWriteDescriptorSet.Buffer descriptorWrite = descriptorSetLayout.makeWriteDescriptorSet(vkDescriptorSets.get(a), buffers.get(a).getHandle(), stack);
//				final VkWriteDescriptorSet.Buffer descriptorWrite = descriptorSetLayout.makeWriteDescriptorSet2(vkDescriptorSets.get(a), buffers, stack);
//				vkUpdateDescriptorSets(vkLogicalDevice, descriptorWrite, null);
//			}
//		}
	}

	public void free(VkDevice vkLogicalDevice) {
		if (vkDescriptorSets != null) {
			memFree(vkDescriptorSets);
		}
		vkDestroyDescriptorPool(vkLogicalDevice, vkDescriptorPool, null);
		vkDestroyDescriptorSetLayout(vkLogicalDevice, vkDescriptorSetLayout, null);
	}

	public long getDescriptorSet(int index) {
		return vkDescriptorSets.get(index);
	}

	public long getDesciptorSetLayout() {
		return vkDescriptorSetLayout;
	}
}
