package ua.rawfish2d.vk2d;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import ua.rawfish2d.vk2d.vkutils.VkHelper;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.vulkan.VK10.*;

@RequiredArgsConstructor
public class VkBuffer {
	private final VkDevice vkLogicalDevice;
	private long vkBuffer;
	private long vkBufferMemory;
	@Getter
	private int bufferSize;
	private long mappedBufferPointer = 0;
	private ByteBuffer buffer;
	private VkBuffer vkStagingBuffer = null;

	public void createBuffer(int bufferSize, int usageFlags) {
		this.bufferSize = bufferSize;

		try (MemoryStack stack = MemoryStack.stackPush()) {
			System.out.printf("Buffer size: %d\n", bufferSize);
			// create vertex buffer
			final VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
					.sType$Default()
					.size(bufferSize)
					.usage(usageFlags)
					.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

			final LongBuffer pBuffer = stack.mallocLong(1);
			final int result = vkCreateBuffer(vkLogicalDevice, bufferInfo, null, pBuffer);
			if (result != VK_SUCCESS) {
				throw new RuntimeException("Failed to create vertex buffer! Error: " + VkHelper.translateVulkanResult(result));
			}
			vkBuffer = pBuffer.get(0);
			System.out.printf("buffer address: %s\n", String.format("0x%08x", vkBuffer));
		}
	}

	public void allocateMemory(VkPhysicalDevice vkPhysicalDevice, int memoryFlags) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			// check available memory types
			final VkMemoryRequirements memRequirements = VkMemoryRequirements.malloc(stack);
			vkGetBufferMemoryRequirements(vkLogicalDevice, vkBuffer, memRequirements);

			final int memoryType = VkHelper.findMemoryType(vkPhysicalDevice, memRequirements.memoryTypeBits(), memoryFlags, stack);

			// allocate memory
			final VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
					.sType$Default()
					.allocationSize(bufferSize)
					.memoryTypeIndex(memoryType);

			final LongBuffer pVertexBufferMemory = stack.mallocLong(1);
			if (vkAllocateMemory(vkLogicalDevice, allocInfo, null, pVertexBufferMemory) != VK_SUCCESS) {
				throw new RuntimeException("Failed to allocate vertex buffer memory!");
			}
			vkBufferMemory = pVertexBufferMemory.get(0);
		}
		// attach/bind memory to vertex buffer
		vkBindBufferMemory(vkLogicalDevice, vkBuffer, vkBufferMemory, 0);
	}

	public long getHandle() {
		return vkBuffer;
	}

	public long getMemoryHandle() {
		return vkBufferMemory;
	}

	public void bindVertexBuffer(VkCommandBuffer commandBuffer) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final LongBuffer pVertexBuffer = stack.longs(this.getHandle());
			final LongBuffer pVertexBufferOffsets = stack.longs(0);
			vkCmdBindVertexBuffers(commandBuffer, 0, pVertexBuffer, pVertexBufferOffsets);
		}
	}

	public void bindIndexBuffer(VkCommandBuffer commandBuffer, int type) {
		vkCmdBindIndexBuffer(commandBuffer, vkBuffer, 0, type);
	}

	public ByteBuffer getStagingBuffer() {
		return vkStagingBuffer.getBuffer();
	}

	public ByteBuffer getBuffer() {
		if (mappedBufferPointer == 0) {
			// map memory and get buffer
			try (MemoryStack stack = MemoryStack.stackPush()) {
				final PointerBuffer pData = stack.mallocPointer(1);
				vkMapMemory(vkLogicalDevice, vkBufferMemory, 0, bufferSize, 0, pData);
				mappedBufferPointer = pData.get(0);
			}
			buffer = memByteBuffer(mappedBufferPointer, bufferSize);
		}
		return buffer;
	}

	public void makeStagingBuffer(VkPhysicalDevice vkPhysicalDevice, int usageFlags) {
		if (vkStagingBuffer != null) {
			return;
		}
		vkStagingBuffer = new VkBuffer(vkLogicalDevice);
		System.out.printf("Creating staging buffer:\n");
		vkStagingBuffer.createBuffer(bufferSize, usageFlags);
		vkStagingBuffer.allocateMemory(vkPhysicalDevice, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
	}

	public void deleteStagingBuffer() {
		if (vkStagingBuffer != null) {
			vkStagingBuffer.destroyAndFreeMemory();
			vkStagingBuffer = null;
		}
	}

	public void destroyAndFreeMemory() {
		deleteStagingBuffer();
		if (mappedBufferPointer != 0) {
			vkUnmapMemory(vkLogicalDevice, vkBufferMemory);
		}
		vkDestroyBuffer(vkLogicalDevice, vkBuffer, null);
		vkFreeMemory(vkLogicalDevice, vkBufferMemory, null);
	}

	public static void gigaBarrier(MemoryStack stack, VkCommandBuffer commandBuffer) {
		final VkMemoryBarrier.Buffer memoryBarriers = VkMemoryBarrier.calloc(1, stack).sType$Default();
		final VkMemoryBarrier vkMemoryBarrier = VkMemoryBarrier.calloc(stack)
				.sType$Default()
				.pNext(0)
				.srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT)
				.dstAccessMask(VK_ACCESS_MEMORY_READ_BIT);
		memoryBarriers.put(0, vkMemoryBarrier);

		vkCmdPipelineBarrier(commandBuffer,
				VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
				VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
				0,
				memoryBarriers,
				null,
				null);
	}

	public void uploadFromStagingBuffer(MemoryStack stack, VkCommandBuffer commandBuffer) {
		final VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
				.srcOffset(0)
				.dstOffset(0)
				.size(bufferSize);
		vkCmdCopyBuffer(commandBuffer, vkStagingBuffer.vkBuffer, vkBuffer, copyRegion);

		VkBuffer.gigaBarrier(stack, commandBuffer); // doesn't look like its doing anything at all
	}
}
