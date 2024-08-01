package ua.rawfish2d.vk2d.vkutils;

import lombok.NonNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.ShadercIncludeResolve;
import org.lwjgl.util.shaderc.ShadercIncludeResult;
import org.lwjgl.util.shaderc.ShadercIncludeResultRelease;
import org.lwjgl.vulkan.*;
import ua.rawfish2d.vk2d.VkBuffer;
import ua.rawfish2d.vk2d.attrib.AttribFormat;
import ua.rawfish2d.vk2d.enums.TextureFiltering;
import ua.rawfish2d.vk2d.enums.TextureWrap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.EXTDebugReport.VK_ERROR_VALIDATION_FAILED_EXT;
import static org.lwjgl.vulkan.EXTSwapchainColorspace.VK_COLOR_SPACE_BT709_NONLINEAR_EXT;
import static org.lwjgl.vulkan.EXTSwapchainColorspace.VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT;
import static org.lwjgl.vulkan.KHRDisplaySwapchain.VK_ERROR_INCOMPATIBLE_DISPLAY_KHR;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.NVRayTracing.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_ERROR_OUT_OF_POOL_MEMORY;

public class VkHelper {
	public static VkPipelineVertexInputStateCreateInfo makeVertexAttribDescription(AttribFormat format, MemoryStack stack) {
		final VkVertexInputAttributeDescription.Buffer attributeDescriptionList =
				switch (format.getBufferLayout()) {
					case INTERLEAVED -> format.getInterleavedDescriptions(stack);
					case SEPARATE -> format.getSeparateDescriptions(stack);
					case SEQUENTIAL ->
							format.getSequentialDescriptions(stack, format.getPrimitiveCount(), format.getVerticesPerPrimitive());
				};

//		int stride = switch (format.getBufferLayout()) {
//			case INTERLEAVED -> format.getStride();
//			case SEPARATE -> format.getStride();
//			case SEQUENTIAL -> 0;
//		};

		final VkVertexInputBindingDescription.Buffer bindingDescription = format.makeVertexInputBindingDescriptions(stack);

//		final VkVertexInputBindingDescription.Buffer bindingDescription = VkVertexInputBindingDescription.calloc(1, stack)
//				.binding(binding)
//				.stride(stride)
//				.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

		return VkPipelineVertexInputStateCreateInfo.calloc(stack)
				.sType$Default()
				.pVertexBindingDescriptions(bindingDescription)
				.pVertexAttributeDescriptions(attributeDescriptionList);
	}

	public static int vkFormatToByteCount(int format) {
		return switch (format) {
			case VK_FORMAT_R8_SINT -> 1; // int

			case VK_FORMAT_R16_SFLOAT -> 2; //
			case VK_FORMAT_R16G16_SFLOAT -> 4; //
			case VK_FORMAT_R16G16B16_SFLOAT -> 6; //
			case VK_FORMAT_R16G16B16A16_SFLOAT -> 8; //

			case VK_FORMAT_R32_SFLOAT -> 4; // float
			case VK_FORMAT_R32G32_SFLOAT -> 8; // vec2
			case VK_FORMAT_R32G32B32_SFLOAT -> 12; // vec3
			case VK_FORMAT_R32G32B32A32_SFLOAT -> 16; // vec4

			case VK_FORMAT_R32_SINT -> 4; // int
			case VK_FORMAT_R32G32_SINT -> 8; // ivec2
			case VK_FORMAT_R32G32B32_SINT -> 12; // ivec3
			case VK_FORMAT_R32G32B32A32_SINT -> 16; // ivec4

			case VK_FORMAT_R32_UINT -> 4; // uint
			case VK_FORMAT_R32G32_UINT -> 8; // uvec2
			case VK_FORMAT_R32G32B32_UINT -> 12; // uvec3
			case VK_FORMAT_R32G32B32A32_UINT -> 16; // uvec4
			default -> throw new IllegalStateException("Unexpected value: " + format);
		};
	}

	public static VkClearValue vkGetClearValue(MemoryStack stack, float r, float g, float b, float a) {
		final VkClearValue clearColor = VkClearValue.calloc(stack);
		clearColor.color().float32()
				.put(0, r)
				.put(1, g)
				.put(2, b)
				.put(3, a);
		return clearColor;
	}

	public static VkClearValue vkGetClearValue(MemoryStack stack, int r, int g, int b, int a) {
		final VkClearValue clearColor = VkClearValue.calloc(stack);
		clearColor.color().float32()
				.put(0, (1f / 255f) * (float) r)
				.put(1, (1f / 255f) * (float) g)
				.put(2, (1f / 255f) * (float) b)
				.put(3, (1f / 255f) * (float) a);
		return clearColor;
	}

	public static int findMemoryType(VkPhysicalDevice vkPhysicalDevice, int typeFilter, int properties, MemoryStack stack) {
		final VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.malloc(stack);
		vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, memProperties);

//		for (int a = 0; a < memProperties.memoryTypeCount(); a++) {
//			final int memoryPropertyFlags = memProperties.memoryTypes().get(a).propertyFlags();
//			System.out.printf("Memory Property[%d]: %s\n", a, VKUtil.vkTranlateMemoryProperty(memoryPropertyFlags));
//		}

		for (int a = 0; a < memProperties.memoryTypeCount(); a++) {
			final int memoryPropertyFlags = memProperties.memoryTypes().get(a).propertyFlags();

			if ((typeFilter & (1 << a)) >= 1 && (memoryPropertyFlags & properties) == properties) {
				System.out.printf("Chose memory index %d with flags: %s\n", a, vkTranslateMemoryProperty(memoryPropertyFlags));
				return a;
			}
		}
		throw new RuntimeException("Failed to find suitable memory type!");
	}

	public static boolean isImageFormatSupported(VkPhysicalDevice vkPhysicalDevice, int format, int type, int tiling, int usage) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkImageFormatProperties vkImageFormatProperties = VkImageFormatProperties.malloc(stack);
			vkGetPhysicalDeviceImageFormatProperties(
					vkPhysicalDevice,
					format,
					type,
					tiling,
					usage,
					0,
					vkImageFormatProperties);

//			System.out.println("===ImageFormatProperties===" +
//					"\n\tmaxExtent:" +
//					"\n\t\tdepth: " + vkImageFormatProperties.maxExtent().depth() +
//					"\n\t\twidth: " + vkImageFormatProperties.maxExtent().width() +
//					"\n\t\theight: " + vkImageFormatProperties.maxExtent().height() +
//					"\n\tmaxArrayLayers: " + vkImageFormatProperties.maxArrayLayers() +
//					"\n\tmaxMipLevels: " + vkImageFormatProperties.maxMipLevels() +
//					"\n\tmaxResourceSize: " + humanReadableByteCountBin(vkImageFormatProperties.maxResourceSize()) + " or " + vkImageFormatProperties.maxResourceSize() + " bytes" +
//					"\n\tsampleCounts: " + vkImageFormatProperties.sampleCounts());

			return vkImageFormatProperties.maxResourceSize() >= 1;
		}
	}

	public static void checkImageFormatSupport(VkPhysicalDevice vkPhysicalDevice) {
		final int[] formats = new int[]{
				VK_FORMAT_R8_SRGB,
				VK_FORMAT_R8G8_SRGB,

				VK_FORMAT_R8G8B8_SRGB,

				VK_FORMAT_B8G8R8_SRGB,

				VK_FORMAT_R16_SFLOAT,
				VK_FORMAT_R16G16_SFLOAT,
				VK_FORMAT_R16G16B16_SFLOAT,
				VK_FORMAT_R16G16B16A16_SFLOAT,

				VK_FORMAT_R8G8B8A8_SRGB,
				VK_FORMAT_R8G8B8A8_UNORM,
				VK_FORMAT_R8G8B8A8_SNORM,
				VK_FORMAT_R8G8B8A8_UINT,
				VK_FORMAT_R8G8B8A8_SINT,
				VK_FORMAT_A8B8G8R8_SRGB_PACK32,
				VK_FORMAT_R5G5B5A1_UNORM_PACK16,
				VK_FORMAT_A2R10G10B10_UNORM_PACK32,
				VK_FORMAT_A2R10G10B10_SNORM_PACK32,
				VK_FORMAT_A2B10G10R10_UNORM_PACK32,
				VK_FORMAT_A2B10G10R10_SNORM_PACK32
		};

		for (int format : formats) {
			final boolean bSupported = isImageFormatSupported(vkPhysicalDevice, format, VK_IMAGE_TYPE_2D, VK_IMAGE_TILING_OPTIMAL, VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
			final String supported = bSupported ? "✅" : "❌";
			System.out.printf("%s format: %s\n", supported, translateSurfaceFormatBit(format));
		}
	}

	public static void createImage(VkDevice vkLogicalDevice, VkPhysicalDevice vkPhysicalDevice, int format, int width, int height, @NonNull LongBuffer out_vkTextureImage, @NonNull LongBuffer out_vkTextureImageMemory) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
					.sType$Default()
					.imageType(VK_IMAGE_TYPE_2D)
					.mipLevels(1)
					.arrayLayers(1)
					.format(format)
					.tiling(VK_IMAGE_TILING_OPTIMAL)
					.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
					.usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
					.sharingMode(VK_SHARING_MODE_EXCLUSIVE) // image will only be used by one queue family
					.samples(VK_SAMPLE_COUNT_1_BIT)
					.flags(0);

			imageInfo.extent().width(width);
			imageInfo.extent().height(height);
			imageInfo.extent().depth(1);

			System.out.printf("Creating image with format: %s\n", translateSurfaceFormatBit(format));
			final LongBuffer pImage = stack.mallocLong(1);
			final int result = vkCreateImage(vkLogicalDevice, imageInfo, null, pImage);
			if (result != VK_SUCCESS) {
				throw new RuntimeException("Failed to create an image! Error: " + translateVulkanResult(result));
			}
			out_vkTextureImage.put(0, pImage.get(0));
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkMemoryRequirements memRequirements = VkMemoryRequirements.malloc(stack);
			vkGetImageMemoryRequirements(vkLogicalDevice, out_vkTextureImage.get(0), memRequirements);

			final int memoryType = findMemoryType(vkPhysicalDevice, memRequirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, stack);
			final VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
					.sType$Default()
					.allocationSize(memRequirements.size())
					.memoryTypeIndex(memoryType);

			System.out.printf("🔷 Allocating %d bytes (%s) of memory\n", memRequirements.size(), humanReadableByteCountBin(memRequirements.size()));
			final LongBuffer pTextureImageMemory = stack.mallocLong(1);
			final int result = vkAllocateMemory(vkLogicalDevice, allocInfo, null, pTextureImageMemory);
			if (result != VK_SUCCESS) {
				throw new RuntimeException("failed to allocate image memory! Error: " + translateVulkanResult(result));
			}
			out_vkTextureImageMemory.put(0, pTextureImageMemory.get(0));
			vkBindImageMemory(vkLogicalDevice, out_vkTextureImage.get(0), out_vkTextureImageMemory.get(0), 0);
		}
	}

	public static VkCommandBuffer beginSingleTimeCommands(VkDevice vkLogicalDevice, long vkCommandPool) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
					.sType$Default()
					.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
					.commandPool(vkCommandPool)
					.commandBufferCount(1);

			final PointerBuffer pCommandBuffer = stack.mallocPointer(1);
			vkAllocateCommandBuffers(vkLogicalDevice, allocInfo, pCommandBuffer);
			final VkCommandBuffer vkCommandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), vkLogicalDevice);

			final VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
					.sType$Default()
					.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

			vkBeginCommandBuffer(vkCommandBuffer, beginInfo);

			return vkCommandBuffer;
		}
	}

	public static void endSingleTimeCommands(VkDevice vkLogicalDevice, long vkCommandPool, VkCommandBuffer commandBuffer, VkQueue graphicsQueue) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			vkEndCommandBuffer(commandBuffer);
			final PointerBuffer pCommandBuffers = stack.pointers(commandBuffer);
			final VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
					.sType$Default()
					.pCommandBuffers(pCommandBuffers);

			vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE);
			vkQueueWaitIdle(graphicsQueue);
			vkFreeCommandBuffers(vkLogicalDevice, vkCommandPool, commandBuffer);
		}
	}

	public static void copyBuffer(MemoryStack stack, VkDevice vkLogicalDevice, long vkCommandPool, VkQueue graphicsQueue, long srcBuffer, long dstBuffer, int size) {
		final VkCommandBuffer commandBuffer = beginSingleTimeCommands(vkLogicalDevice, vkCommandPool);
		final VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
				.size(size);

		vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion);
		endSingleTimeCommands(vkLogicalDevice, vkCommandPool, commandBuffer, graphicsQueue);
	}

	public static void beginCommandBuffer(VkCommandBuffer commandBuffer) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
					.sType$Default()
					.flags(0) // VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT
					.pInheritanceInfo(null);

			vkBeginCommandBuffer(commandBuffer, beginInfo);
		}
	}

	public static void endCommandBuffer(VkCommandBuffer commandBuffer) {
		vkEndCommandBuffer(commandBuffer);
	}

	public static void transitionImageLayout(VkDevice vkLogicalDevice, long vkCommandPool, VkQueue graphicsQueue, long vkImage, int vkOldLayout, int vkNewLayout) {
		final VkCommandBuffer commandBuffer = beginSingleTimeCommands(vkLogicalDevice, vkCommandPool);

		try (MemoryStack stack = MemoryStack.stackPush()) {

			int sourceStage;
			int destinationStage;
			int srcAccessMask;
			int dstAccessMask;

			if (vkOldLayout == VK_IMAGE_LAYOUT_UNDEFINED && vkNewLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
				srcAccessMask = 0;
				dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;

				sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
				destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
			} else if (vkOldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && vkNewLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
				srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
				dstAccessMask = VK_ACCESS_SHADER_READ_BIT;

				sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
				destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
			} else {
				throw new RuntimeException("Unsupported layout transition!");
			}

			final VkImageMemoryBarrier.Buffer vkImageMemoryBarrier = VkImageMemoryBarrier.calloc(1, stack)
					.sType$Default()
					// VK_IMAGE_LAYOUT_UNDEFINED if you don't care about existing data in texture
					.oldLayout(vkOldLayout)
					.newLayout(vkNewLayout)
					// should be actual indexes if you transition queue ownership, but not this time
					.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
					.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
					.image(vkImage)
					.srcAccessMask(srcAccessMask)
					.dstAccessMask(dstAccessMask);
			vkImageMemoryBarrier.subresourceRange()
					.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
					.baseMipLevel(0)
					.levelCount(1)
					.baseArrayLayer(0)
					.layerCount(1);

			vkCmdPipelineBarrier(
					commandBuffer,
					// specifies in which pipeline stage the operations occur that should happen before the barrier
					sourceStage,
					// specifies the pipeline stage in which operations will wait on the barrier
					destinationStage,
					// 0 or VK_DEPENDENCY_BY_REGION_BIT
					0,
					null,
					null,
					vkImageMemoryBarrier);
		}

		endSingleTimeCommands(vkLogicalDevice, vkCommandPool, commandBuffer, graphicsQueue);
	}

	public static void copyBufferToImage(VkDevice vkLogicalDevice, long vkCommandPool, VkQueue graphicsQueue, VkBuffer buffer, long image, int width, int height) {
		final VkCommandBuffer commandBuffer = beginSingleTimeCommands(vkLogicalDevice, vkCommandPool);

		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkBufferImageCopy.Buffer vkBufferImageCopy = VkBufferImageCopy.calloc(1, stack)
					.bufferOffset(0)
					.bufferRowLength(0)
					.bufferImageHeight(0);
			vkBufferImageCopy.imageOffset().set(0, 0, 0);
			vkBufferImageCopy.imageExtent().set(width, height, 1);
			vkBufferImageCopy.imageSubresource()
					.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
					.mipLevel(0)
					.baseArrayLayer(0)
					.layerCount(1);

			vkCmdCopyBufferToImage(
					commandBuffer,
					buffer.getHandle(),
					image,
					VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
					vkBufferImageCopy);
		}

		endSingleTimeCommands(vkLogicalDevice, vkCommandPool, commandBuffer, graphicsQueue);
	}

	public static long createTextureImageView(VkDevice vkLogicalDevice, long vkTextureImage, int vkFormat) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkImageViewCreateInfo vkImageViewCreateInfo = VkImageViewCreateInfo.calloc(stack)
					.sType$Default()
					.image(vkTextureImage)
					.viewType(VK_IMAGE_VIEW_TYPE_2D)
					.format(vkFormat);
			vkImageViewCreateInfo.subresourceRange()
					.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
					.baseMipLevel(0)
					.levelCount(1)
					.baseArrayLayer(0)
					.layerCount(1);

			final LongBuffer pTextureImageView = stack.mallocLong(1);
			final int result = vkCreateImageView(vkLogicalDevice, vkImageViewCreateInfo, null, pTextureImageView);
			if (result != VK_SUCCESS) {
				throw new RuntimeException("Failed to create texture image view! Error: " + translateVulkanResult(result));
			}
			return pTextureImageView.get(0);
		}
	}

	public static String humanReadableByteCountBin(long bytes) {
		long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
		if (absB < 1024) {
			return bytes + " B";
		}
		long value = absB;
		CharacterIterator ci = new StringCharacterIterator("KMGTPE");
		for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
			value >>= 10;
			ci.next();
		}
		value *= Long.signum(bytes);
		return String.format(Locale.US, "%.1f %ciB", value / 1024.0, ci.current());
	}

	public static VkSamplerCreateInfo makeSamplerCreateInfo(MemoryStack stack, float maxAnisotropy, TextureFiltering minFilter, TextureFiltering magFilter, TextureFiltering mipmapFilter, TextureWrap textureWrapU, TextureWrap textureWrapV) {
		boolean anisotropyEnabled = maxAnisotropy != 0f;
		return VkSamplerCreateInfo.calloc(stack)
				.sType$Default()
				.minFilter(minFilter.get())
				.magFilter(magFilter.get())
				.addressModeU(textureWrapU.get())
				.addressModeV(textureWrapV.get())
				.addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
				.anisotropyEnable(anisotropyEnabled)
				.maxAnisotropy(maxAnisotropy)
				.borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
				.unnormalizedCoordinates(false)
				// stuff for shadow maps
				.compareEnable(false)
				.compareOp(VK_COMPARE_OP_ALWAYS)
				//
				.mipmapMode(mipmapFilter.get())
				.mipLodBias(0f)
				.minLod(0f)
				.maxLod(0f);
	}

	// from VKUtil
	public static void _CHECK_(int ret, String msg) {
		if (ret != VK_SUCCESS) {
			throw new AssertionError(msg + ": " + translateVulkanResult(ret));
		}
	}

	private static int vulkanStageToShadercKind(int stage) {
		switch (stage) {
			case VK_SHADER_STAGE_VERTEX_BIT:
				return shaderc_vertex_shader;
			case VK_SHADER_STAGE_FRAGMENT_BIT:
				return shaderc_fragment_shader;
			case VK_SHADER_STAGE_RAYGEN_BIT_NV:
				return shaderc_raygen_shader;
			case VK_SHADER_STAGE_CLOSEST_HIT_BIT_NV:
				return shaderc_closesthit_shader;
			case VK_SHADER_STAGE_MISS_BIT_NV:
				return shaderc_miss_shader;
			case VK_SHADER_STAGE_ANY_HIT_BIT_NV:
				return shaderc_anyhit_shader;
			default:
				throw new IllegalArgumentException("Stage: " + stage);
		}
	}

	public static ByteBuffer glslToSpirv(String classPath, int vulkanStage) throws IOException {
		// ByteBuffer src = IOUtils.ioResourceToByteBuffer(classPath, 1024);
		ByteBuffer src = IOUtils.ioFileToByteBuffer(classPath, 1024);
		long compiler = shaderc_compiler_initialize();
		long options = shaderc_compile_options_initialize();
		ShadercIncludeResolve resolver;
		ShadercIncludeResultRelease releaser;
		shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_2);
		shaderc_compile_options_set_target_spirv(options, shaderc_spirv_version_1_3);
		shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);
		shaderc_compile_options_set_include_callbacks(options, resolver = new ShadercIncludeResolve() {
			public long invoke(long user_data, long requested_source, int type, long requesting_source, long include_depth) {
				ShadercIncludeResult res = ShadercIncludeResult.calloc();
				try {
					String src = classPath.substring(0, classPath.lastIndexOf('/')) + "/" + memUTF8(requested_source);
					res.content(IOUtils.ioResourceToByteBuffer(src, 1024));
					res.source_name(memUTF8(src));
					return res.address();
				} catch (IOException e) {
					throw new AssertionError("Failed to resolve include: " + src);
				}
			}
		}, releaser = new ShadercIncludeResultRelease() {
			public void invoke(long user_data, long include_result) {
				ShadercIncludeResult result = ShadercIncludeResult.create(include_result);
				MemoryUtil.memFree(result.source_name());
				result.free();
			}
		}, 0L);
		long res;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			res = shaderc_compile_into_spv(compiler, src, vulkanStageToShadercKind(vulkanStage), stack.UTF8(classPath), stack.UTF8("main"), options);
			if (res == 0L)
				throw new AssertionError("Internal error during compilation!");
		}
		if (shaderc_result_get_compilation_status(res) != shaderc_compilation_status_success) {
			throw new AssertionError("Shader compilation failed: " + shaderc_result_get_error_message(res));
		}
		int size = (int) shaderc_result_get_length(res);
		ByteBuffer resultBytes = createByteBuffer(size);
		resultBytes.put(shaderc_result_get_bytes(res));
		resultBytes.flip();
		shaderc_result_release(res);
		shaderc_compiler_release(compiler);
		releaser.free();
		resolver.free();
		return resultBytes;
	}

	/**
	 * Translates a Vulkan {@code VkResult} value to a String describing the result.
	 *
	 * @param result the {@code VkResult} value
	 * @return the result description
	 */
	public static String translateVulkanResult(int result) {
		return switch (result) {
			// Success codes
			case VK_SUCCESS -> "Command successfully completed.";
			case VK_NOT_READY -> "A fence or query has not yet completed.";
			case VK_TIMEOUT -> "A wait operation has not completed in the specified time.";
			case VK_EVENT_SET -> "An event is signaled.";
			case VK_EVENT_RESET -> "An event is unsignaled.";
			case VK_INCOMPLETE -> "A return array was too small for the result.";
			case VK_SUBOPTIMAL_KHR ->
					"A swapchain no longer matches the surface properties exactly, but can still be used to present to the surface successfully.";

			// Error codes
			case VK_ERROR_OUT_OF_HOST_MEMORY -> "A host memory allocation has failed.";
			case VK_ERROR_OUT_OF_DEVICE_MEMORY -> "A device memory allocation has failed.";
			case VK_ERROR_INITIALIZATION_FAILED ->
					"Initialization of an object could not be completed for implementation-specific reasons.";
			case VK_ERROR_DEVICE_LOST -> "The logical or physical device has been lost.";
			case VK_ERROR_MEMORY_MAP_FAILED -> "Mapping of a memory object has failed.";
			case VK_ERROR_LAYER_NOT_PRESENT -> "A requested layer is not present or could not be loaded.";
			case VK_ERROR_EXTENSION_NOT_PRESENT -> "A requested extension is not supported.";
			case VK_ERROR_FEATURE_NOT_PRESENT -> "A requested feature is not supported.";
			case VK_ERROR_INCOMPATIBLE_DRIVER ->
					"The requested version of Vulkan is not supported by the driver or is otherwise incompatible for implementation-specific reasons.";
			case VK_ERROR_TOO_MANY_OBJECTS -> "Too many objects of the type have already been created.";
			case VK_ERROR_FORMAT_NOT_SUPPORTED -> "A requested format is not supported on this device.";
			case VK_ERROR_FRAGMENTED_POOL -> "VK_ERROR_FRAGMENTED_POOL";
			case VK_ERROR_UNKNOWN -> "Unknown error.";
			case VK_ERROR_SURFACE_LOST_KHR -> "A surface is no longer available.";
			case VK_ERROR_NATIVE_WINDOW_IN_USE_KHR ->
					"The requested window is already connected to a VkSurfaceKHR, or to some other non-Vulkan API.";
			case VK_ERROR_OUT_OF_DATE_KHR ->
					"A surface has changed in such a way that it is no longer compatible with the swapchain, and further presentation requests using the "
							+ "swapchain will fail. Applications must query the new surface properties and recreate their swapchain if they wish to continue" + "presenting to the surface.";
			case VK_ERROR_INCOMPATIBLE_DISPLAY_KHR ->
					"The display used by a swapchain does not use the same presentable image layout, or is incompatible in a way that prevents sharing an" + " image.";
			case VK_ERROR_VALIDATION_FAILED_EXT -> "A validation layer found an error.";
			case VK_ERROR_OUT_OF_POOL_MEMORY -> "VK_ERROR_OUT_OF_POOL_MEMORY";
			default -> String.format("%s [%d]", "Unknown", result);
		};
	}

	public static String translatePhysicalDeviceType(int deviceType) {
		return switch (deviceType) {
			case VK_PHYSICAL_DEVICE_TYPE_OTHER -> "VK_PHYSICAL_DEVICE_TYPE_OTHER";
			case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> "VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU";
			case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> "VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU";
			case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU -> "VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU";
			case VK_PHYSICAL_DEVICE_TYPE_CPU -> "VK_PHYSICAL_DEVICE_TYPE_CPU";
			default -> "UNKNOWN";
		};
	}

	private static Set<String> getAvailableLayers() {
		final Set<String> res = new HashSet<>();
		final int[] ip = new int[1];
		vkEnumerateInstanceLayerProperties(ip, null);
		final int count = ip[0];
		System.out.println("Layers count:" + count);
		System.out.println("Layers:");

		try (final MemoryStack stack = MemoryStack.stackPush()) {
			if (count > 0) {
				final VkLayerProperties.Buffer instanceLayers = VkLayerProperties.malloc(count, stack);
				vkEnumerateInstanceLayerProperties(ip, instanceLayers);
				for (int i = 0; i < count; i++) {
					final String layerName = instanceLayers.get(i).layerNameString();
					System.out.println("layerName:" + layerName);
					res.add(layerName);
				}
			}
		}

		return res;
	}

	public static String translateQueueBit(int bits) {
		final List<String> stringList = new ArrayList<>();
		if ((bits & VK_QUEUE_GRAPHICS_BIT) == 1) {
			stringList.add("VK_QUEUE_GRAPHICS_BIT");
		}
		if ((bits & VK_QUEUE_COMPUTE_BIT) == 2) {
			stringList.add("VK_QUEUE_COMPUTE_BIT");
		}
		if ((bits & VK_QUEUE_TRANSFER_BIT) == 4) {
			stringList.add("VK_QUEUE_TRANSFER_BIT");
		}
		if ((bits & VK_QUEUE_SPARSE_BINDING_BIT) == 8) {
			stringList.add("VK_QUEUE_SPARSE_BINDING_BIT");
		}
		if ((bits & VK_QUEUE_FAMILY_IGNORED) == -1) {
			stringList.add("VK_QUEUE_FAMILY_IGNORED");
		}
		if ((bits & VK_QUEUE_FAMILY_EXTERNAL) == -2) {
			stringList.add("VK_QUEUE_FAMILY_EXTERNAL");
		}
		final StringBuilder stringBuilder = new StringBuilder();
		for (int a = 0; a < stringList.size(); ++a) {
			stringBuilder.append(stringList.get(a));
			if (a != stringList.size() - 1) {
				stringBuilder.append(", ");
			}
		}
		return stringBuilder.toString();
	}

	public static String translateSurfaceFormatBit(int bits) {
		return switch (bits) {
			case VK_FORMAT_R4G4_UNORM_PACK8 -> "VK_FORMAT_R4G4_UNORM_PACK8";
			case VK_FORMAT_R4G4B4A4_UNORM_PACK16 -> "VK_FORMAT_R4G4B4A4_UNORM_PACK16";
			case VK_FORMAT_B4G4R4A4_UNORM_PACK16 -> "VK_FORMAT_B4G4R4A4_UNORM_PACK16";
			case VK_FORMAT_R5G6B5_UNORM_PACK16 -> "VK_FORMAT_R5G6B5_UNORM_PACK16";
			case VK_FORMAT_B5G6R5_UNORM_PACK16 -> "VK_FORMAT_B5G6R5_UNORM_PACK16";
			case VK_FORMAT_R5G5B5A1_UNORM_PACK16 -> "VK_FORMAT_R5G5B5A1_UNORM_PACK16";
			case VK_FORMAT_B5G5R5A1_UNORM_PACK16 -> "VK_FORMAT_B5G5R5A1_UNORM_PACK16";
			case VK_FORMAT_A1R5G5B5_UNORM_PACK16 -> "VK_FORMAT_A1R5G5B5_UNORM_PACK16";

			case VK_FORMAT_R8_UNORM -> "VK_FORMAT_R8_UNORM";
			case VK_FORMAT_R8_SNORM -> "VK_FORMAT_R8_SNORM";
			case VK_FORMAT_R8_USCALED -> "VK_FORMAT_R8_USCALED";
			case VK_FORMAT_R8_SSCALED -> "VK_FORMAT_R8_SSCALED";
			case VK_FORMAT_R8_UINT -> "VK_FORMAT_R8_UINT";
			case VK_FORMAT_R8_SINT -> "VK_FORMAT_R8_SINT";
			case VK_FORMAT_R8_SRGB -> "VK_FORMAT_R8_SRGB";

			case VK_FORMAT_R8G8_UNORM -> "VK_FORMAT_R8G8_UNORM";
			case VK_FORMAT_R8G8_SNORM -> "VK_FORMAT_R8G8_SNORM";
			case VK_FORMAT_R8G8_USCALED -> "VK_FORMAT_R8G8_USCALED";
			case VK_FORMAT_R8G8_SSCALED -> "VK_FORMAT_R8G8_SSCALED";
			case VK_FORMAT_R8G8_UINT -> "VK_FORMAT_R8G8_UINT";
			case VK_FORMAT_R8G8_SINT -> "VK_FORMAT_R8G8_SINT";
			case VK_FORMAT_R8G8_SRGB -> "VK_FORMAT_R8G8_SRGB";

			case VK_FORMAT_R8G8B8_UNORM -> "VK_FORMAT_R8G8B8_UNORM";
			case VK_FORMAT_R8G8B8_SNORM -> "VK_FORMAT_R8G8B8_SNORM";
			case VK_FORMAT_R8G8B8_USCALED -> "VK_FORMAT_R8G8B8_USCALED";
			case VK_FORMAT_R8G8B8_SSCALED -> "VK_FORMAT_R8G8B8_SSCALED";
			case VK_FORMAT_R8G8B8_UINT -> "VK_FORMAT_R8G8B8_UINT";
			case VK_FORMAT_R8G8B8_SINT -> "VK_FORMAT_R8G8B8_SINT";
			case VK_FORMAT_R8G8B8_SRGB -> "VK_FORMAT_R8G8B8_SRGB";

			case VK_FORMAT_B8G8R8_UNORM -> "VK_FORMAT_B8G8R8_UNORM";
			case VK_FORMAT_B8G8R8_SNORM -> "VK_FORMAT_B8G8R8_SNORM";
			case VK_FORMAT_B8G8R8_USCALED -> "VK_FORMAT_B8G8R8_USCALED";
			case VK_FORMAT_B8G8R8_SSCALED -> "VK_FORMAT_B8G8R8_SSCALED";
			case VK_FORMAT_B8G8R8_UINT -> "VK_FORMAT_B8G8R8_UINT";
			case VK_FORMAT_B8G8R8_SINT -> "VK_FORMAT_B8G8R8_SINT";
			case VK_FORMAT_B8G8R8_SRGB -> "VK_FORMAT_B8G8R8_SRGB";

			case VK_FORMAT_R8G8B8A8_UNORM -> "VK_FORMAT_R8G8B8A8_UNORM";
			case VK_FORMAT_R8G8B8A8_SNORM -> "VK_FORMAT_R8G8B8A8_SNORM";
			case VK_FORMAT_R8G8B8A8_USCALED -> "VK_FORMAT_R8G8B8A8_USCALED";
			case VK_FORMAT_R8G8B8A8_SSCALED -> "VK_FORMAT_R8G8B8A8_SSCALED";
			case VK_FORMAT_R8G8B8A8_UINT -> "VK_FORMAT_R8G8B8A8_UINT";
			case VK_FORMAT_R8G8B8A8_SINT -> "VK_FORMAT_R8G8B8A8_SINT";
			case VK_FORMAT_R8G8B8A8_SRGB -> "VK_FORMAT_R8G8B8A8_SRGB";

			case VK_FORMAT_B8G8R8A8_UNORM -> "VK_FORMAT_B8G8R8A8_UNORM";
			case VK_FORMAT_B8G8R8A8_SNORM -> "VK_FORMAT_B8G8R8A8_SNORM";
			case VK_FORMAT_B8G8R8A8_USCALED -> "VK_FORMAT_B8G8R8A8_USCALED";
			case VK_FORMAT_B8G8R8A8_SSCALED -> "VK_FORMAT_B8G8R8A8_SSCALED";
			case VK_FORMAT_B8G8R8A8_UINT -> "VK_FORMAT_B8G8R8A8_UINT";
			case VK_FORMAT_B8G8R8A8_SINT -> "VK_FORMAT_B8G8R8A8_SINT";
			case VK_FORMAT_B8G8R8A8_SRGB -> "VK_FORMAT_B8G8R8A8_SRGB";

			case VK_FORMAT_A8B8G8R8_UNORM_PACK32 -> "VK_FORMAT_A8B8G8R8_UNORM_PACK32";
			case VK_FORMAT_A8B8G8R8_SNORM_PACK32 -> "VK_FORMAT_A8B8G8R8_SNORM_PACK32";
			case VK_FORMAT_A8B8G8R8_SRGB_PACK32 -> "VK_FORMAT_A8B8G8R8_SRGB_PACK32";
			case VK_FORMAT_A2R10G10B10_UNORM_PACK32 -> "VK_FORMAT_A2R10G10B10_UNORM_PACK32";
			case VK_FORMAT_A2R10G10B10_SNORM_PACK32 -> "VK_FORMAT_A2R10G10B10_SNORM_PACK32";
			case VK_FORMAT_A2B10G10R10_UNORM_PACK32 -> "VK_FORMAT_A2B10G10R10_UNORM_PACK32";
			case VK_FORMAT_A2B10G10R10_SNORM_PACK32 -> "VK_FORMAT_A2B10G10R10_SNORM_PACK32";

			case VK_FORMAT_R16_UNORM -> "VK_FORMAT_R16_UNORM";
			case VK_FORMAT_R16_SNORM -> "VK_FORMAT_R16_SNORM";
			case VK_FORMAT_R16_USCALED -> "VK_FORMAT_R16_USCALED";
			case VK_FORMAT_R16_SSCALED -> "VK_FORMAT_R16_SSCALED";
			case VK_FORMAT_R16_UINT -> "VK_FORMAT_R16_UINT";
			case VK_FORMAT_R16_SINT -> "VK_FORMAT_R16_SINT";
			case VK_FORMAT_R16_SFLOAT -> "VK_FORMAT_R16_SFLOAT";

			case VK_FORMAT_R16G16_UNORM -> "VK_FORMAT_R16G16_UNORM";
			case VK_FORMAT_R16G16_SFLOAT -> "VK_FORMAT_R16G16_SFLOAT";

			case VK_FORMAT_R16G16B16_UNORM -> "VK_FORMAT_R16G16B16_UNORM";
			case VK_FORMAT_R16G16B16_SFLOAT -> "VK_FORMAT_R16G16B16_SFLOAT";

			case VK_FORMAT_R16G16B16A16_UNORM -> "VK_FORMAT_R16G16B16A16_UNORM";
			case VK_FORMAT_R16G16B16A16_SNORM -> "VK_FORMAT_R16G16B16A16_SNORM";
			case VK_FORMAT_R16G16B16A16_SFLOAT -> "VK_FORMAT_R16G16B16A16_SFLOAT";

			case VK_FORMAT_B10G11R11_UFLOAT_PACK32 -> "VK_FORMAT_B10G11R11_UFLOAT_PACK32";

			case VK_FORMAT_R32_UINT -> "VK_FORMAT_R32_UINT"; // uint
			case VK_FORMAT_R32_SINT -> "VK_FORMAT_R32_SINT"; // int
			case VK_FORMAT_R32_SFLOAT -> "VK_FORMAT_R32_SFLOAT"; // float

			case VK_FORMAT_R32G32_UINT -> "VK_FORMAT_R32G32_UINT"; // uvec2
			case VK_FORMAT_R32G32_SINT -> "VK_FORMAT_R32G32_SINT"; // ivec2
			case VK_FORMAT_R32G32_SFLOAT -> "VK_FORMAT_R32G32_SFLOAT"; // vec2

			case VK_FORMAT_R32G32B32_UINT -> "VK_FORMAT_R32G32B32_UINT"; // uvec3
			case VK_FORMAT_R32G32B32_SINT -> "VK_FORMAT_R32G32B32_SINT"; // ivec3
			case VK_FORMAT_R32G32B32_SFLOAT -> "VK_FORMAT_R32G32B32_SFLOAT"; // vec3

			case VK_FORMAT_R32G32B32A32_UINT -> "VK_FORMAT_R32G32B32A32_UINT"; // uvec4
			case VK_FORMAT_R32G32B32A32_SINT -> "VK_FORMAT_R32G32B32A32_SINT"; // ivec4
			case VK_FORMAT_R32G32B32A32_SFLOAT -> "VK_FORMAT_R32G32B32A32_SFLOAT"; // vec4

			case VK_FORMAT_R64_UINT -> "VK_FORMAT_R64_UINT"; // unsigned double ?
			case VK_FORMAT_R64_SINT -> "VK_FORMAT_R64_UINT"; // singled double ?
			case VK_FORMAT_R64_SFLOAT -> "VK_FORMAT_R64_UINT"; // double ?

			default -> "VK_FORMAT_UNDEFINED";
		};
	}

	public static String translateColorSpace(int bits) {
		return switch (bits) {
			case VK_COLOR_SPACE_SRGB_NONLINEAR_KHR -> "VK_COLOR_SPACE_SRGB_NONLINEAR_KHR";
			case VK_COLOR_SPACE_BT709_NONLINEAR_EXT -> "VK_COLOR_SPACE_BT709_NONLINEAR_EXT"; // EXT
			case VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT -> "VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT"; // EXT
			default -> "unknown";
		};
	}

	public static String translateFormatFeature(int bits) {
		final List<String> stringList = new ArrayList<>();
		if ((bits & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) == VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) {
			stringList.add("VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT");
		}
		if ((bits & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) == VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) {
			stringList.add("VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT");
		}
		if ((bits & VK_FORMAT_FEATURE_STORAGE_IMAGE_ATOMIC_BIT) == VK_FORMAT_FEATURE_STORAGE_IMAGE_ATOMIC_BIT) {
			stringList.add("VK_FORMAT_FEATURE_STORAGE_IMAGE_ATOMIC_BIT");
		}
		if ((bits & VK_FORMAT_FEATURE_UNIFORM_TEXEL_BUFFER_BIT) == VK_FORMAT_FEATURE_UNIFORM_TEXEL_BUFFER_BIT) {
			stringList.add("VK_FORMAT_FEATURE_UNIFORM_TEXEL_BUFFER_BIT");
		}
		if ((bits & VK_FORMAT_FEATURE_STORAGE_TEXEL_BUFFER_BIT) == VK_FORMAT_FEATURE_STORAGE_TEXEL_BUFFER_BIT) {
			stringList.add("VK_FORMAT_FEATURE_STORAGE_TEXEL_BUFFER_BIT");
		}
		if ((bits & VK_FORMAT_FEATURE_STORAGE_TEXEL_BUFFER_ATOMIC_BIT) == VK_FORMAT_FEATURE_STORAGE_TEXEL_BUFFER_ATOMIC_BIT) {
			stringList.add("VK_FORMAT_FEATURE_STORAGE_TEXEL_BUFFER_ATOMIC_BIT");
		}
		if ((bits & VK_FORMAT_FEATURE_VERTEX_BUFFER_BIT) == VK_FORMAT_FEATURE_VERTEX_BUFFER_BIT) {
			stringList.add("VK_FORMAT_FEATURE_VERTEX_BUFFER_BIT");
		}
		if ((bits & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT) == VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT) {
			stringList.add("VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT");
		}
		if ((bits & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BLEND_BIT) == VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BLEND_BIT) {
			stringList.add("VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BLEND_BIT");
		}
		if ((bits & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) == VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) {
			stringList.add("VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT");
		}
		if ((bits & VK_FORMAT_FEATURE_BLIT_SRC_BIT) == VK_FORMAT_FEATURE_BLIT_SRC_BIT) {
			stringList.add("VK_FORMAT_FEATURE_BLIT_SRC_BIT");
		}
		if ((bits & VK_FORMAT_FEATURE_BLIT_DST_BIT) == VK_FORMAT_FEATURE_BLIT_DST_BIT) {
			stringList.add("VK_FORMAT_FEATURE_BLIT_DST_BIT");
		}
		if ((bits & VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) == VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) {
			stringList.add("VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT");
		}
		final StringBuilder stringBuilder = new StringBuilder();
		for (int a = 0; a < stringList.size(); ++a) {
			stringBuilder.append(stringList.get(a));
			if (a != stringList.size() - 1) {
				stringBuilder.append(", ");
			}
		}
		return stringBuilder.toString();
	}

	public static String vkTranslateMemoryProperty(int bits) {
		final List<String> stringList = new ArrayList<>();
		if ((bits & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) == VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) {
			stringList.add("VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT");
		}
		if ((bits & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) == VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) {
			stringList.add("VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT");
		}
		if ((bits & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) == VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) {
			stringList.add("VK_MEMORY_PROPERTY_HOST_COHERENT_BIT");
		}
		if ((bits & VK_MEMORY_PROPERTY_HOST_CACHED_BIT) == VK_MEMORY_PROPERTY_HOST_CACHED_BIT) {
			stringList.add("VK_MEMORY_PROPERTY_HOST_CACHED_BIT");
		}
		if ((bits & VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT) == VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT) {
			stringList.add("VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT");
		}
		final StringBuilder stringBuilder = new StringBuilder();
		for (int a = 0; a < stringList.size(); ++a) {
			stringBuilder.append(stringList.get(a));
			if (a != stringList.size() - 1) {
				stringBuilder.append(", ");
			}
		}
		return stringBuilder.toString();
	}

	public static String translateDescriptorType(int type) {
		return switch (type) {
			case VK_DESCRIPTOR_TYPE_SAMPLER -> "VK_DESCRIPTOR_TYPE_SAMPLER";
			case VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER -> "VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER";
			case VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE -> "VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE";
			case VK_DESCRIPTOR_TYPE_STORAGE_IMAGE -> "VK_DESCRIPTOR_TYPE_STORAGE_IMAGE";
			case VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER -> "VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER";
			case VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER -> "VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER";
			case VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER -> "VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER";
			case VK_DESCRIPTOR_TYPE_STORAGE_BUFFER -> "VK_DESCRIPTOR_TYPE_STORAGE_BUFFER";
			case VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC -> "VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC";
			case VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC -> "VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC";
			case VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT -> "VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT";
			case VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_NV -> "VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_NV";
			default -> throw new IllegalStateException("Unexpected value: " + type);
		};
	}

	public static String translateShaderStage(int stage) {
		return switch (stage) {
			case VK_SHADER_STAGE_VERTEX_BIT -> "VK_SHADER_STAGE_VERTEX_BIT";
			case VK_SHADER_STAGE_FRAGMENT_BIT -> "VK_SHADER_STAGE_FRAGMENT_BIT";
			case VK_SHADER_STAGE_COMPUTE_BIT -> "VK_SHADER_STAGE_COMPUTE_BIT";
			case VK_SHADER_STAGE_GEOMETRY_BIT -> "VK_SHADER_STAGE_GEOMETRY_BIT";
			case VK_SHADER_STAGE_ALL_GRAPHICS -> "VK_SHADER_STAGE_ALL_GRAPHICS";
			case VK_SHADER_STAGE_RAYGEN_BIT_NV -> "VK_SHADER_STAGE_RAYGEN_BIT_NV";
			case VK_SHADER_STAGE_CLOSEST_HIT_BIT_NV -> "VK_SHADER_STAGE_CLOSEST_HIT_BIT_NV";
			case VK_SHADER_STAGE_MISS_BIT_NV -> "VK_SHADER_STAGE_MISS_BIT_NV";
			case VK_SHADER_STAGE_ANY_HIT_BIT_NV -> "VK_SHADER_STAGE_ANY_HIT_BIT_NV";
			default -> throw new IllegalArgumentException("Unexpected stage: " + stage);
		};
	}

	/* Spin-yield loop based alternative to Thread.sleep
	 * Based on the code of Andy Malakov
	 * http://andy-malakov.blogspot.fr/2010/06/alternative-to-threadsleep.html
	 */
	public static void sleepNanos(long nanoDuration) {
		try {
			final long SLEEP_PRECISION = TimeUnit.MILLISECONDS.toNanos(2);
			final long SPIN_YIELD_PRECISION = TimeUnit.MILLISECONDS.toNanos(2);

			final long end = System.nanoTime() + nanoDuration;
			long timeLeft = nanoDuration;
			do {
				if (timeLeft > SLEEP_PRECISION) {
					Thread.sleep(1);
				} else {
					if (timeLeft > SPIN_YIELD_PRECISION) {
						Thread.yield();
					}
				}
				timeLeft = end - System.nanoTime();

				if (Thread.interrupted())
					throw new InterruptedException();
			} while (timeLeft > 0);
		} catch (Exception ex) {
		}
	}

	public static long createCommandPool(VkDevice vkLogicalDevice, int queueIndex) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
					.sType$Default()
					.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
					.queueFamilyIndex(queueIndex);

			final LongBuffer pCommandPool = stack.mallocLong(1);
			if (vkCreateCommandPool(vkLogicalDevice, poolInfo, null, pCommandPool) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create command pool!");
			}
			return pCommandPool.get(0);
		}
	}

	public static VkCommandBuffer createCommandBuffer(VkDevice vkLogicalDevice, long vkCommandPool) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
					.sType$Default()
					.commandPool(vkCommandPool)
					.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
					.commandBufferCount(1);

			final PointerBuffer pCommandBuffer = stack.mallocPointer(1);
			if (vkAllocateCommandBuffers(vkLogicalDevice, allocInfo, pCommandBuffer) != VK_SUCCESS) {
				throw new RuntimeException("Failed to allocate command buffers!");
			}
			return new VkCommandBuffer(pCommandBuffer.get(0), vkLogicalDevice);
		}
	}
}
