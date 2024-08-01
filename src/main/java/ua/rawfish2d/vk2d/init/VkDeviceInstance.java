package ua.rawfish2d.vk2d.init;

import lombok.Getter;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import ua.rawfish2d.vk2d.vkutils.VkHelper;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;

@Getter
public class VkDeviceInstance {
	private static final List<String> validationLayers = new ArrayList<>();
	private static final List<String> requiredDeviceExtensions = new ArrayList<>();
	//	private QueueIndices queueIndices;
	private boolean enableValidationLayers;
	private VkInstance vkInstance;
	private VkPhysicalDevice vkPhysicalDevice;
	private VkDevice vkLogicalDevice;
	private long vkSurface;
	private final List<VkQueue2> vkQueueDataList = new ArrayList<>();
	//	private VkQueue vkGraphicsQueue;
//	private VkQueue vkPresentQueue;
//	private VkQueue vkTransferQueue;
	private boolean overlappingQueues;

	static {
		validationLayers.add("VK_LAYER_KHRONOS_validation");
		requiredDeviceExtensions.add(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
		requiredDeviceExtensions.add(VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME);
//		requiredDeviceExtensions.add("VK_KHR_synchronization2");
	}

	public void create(long windowHandle, boolean enableValidationLayers, boolean overlappingQueues) {
		this.overlappingQueues = overlappingQueues;
		this.enableValidationLayers = enableValidationLayers;
		createInstance(enableValidationLayers);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final LongBuffer pSurface = stack.mallocLong(1);
			final int result = glfwCreateWindowSurface(vkInstance, windowHandle, null, pSurface);
			if (result != VK_SUCCESS) {
				throw new AssertionError("Failed to create window surface! Error: " + VkHelper.translateVulkanResult(result));
			}
			vkSurface = pSurface.get(0);
		}
		pickPhysicalDevice();
		createLogicalDevice();
	}

	public void createInstance(boolean enableValidationLayers) {
		if (enableValidationLayers && !checkValidationLayerSupport()) {
			throw new RuntimeException("Validation layers requested, but not available!");
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkApplicationInfo vkAppInto = VkApplicationInfo.calloc(stack)
					.sType$Default()
					.pApplicationName(stack.UTF8("Vulkan 2D"))
					.applicationVersion(VK_MAKE_VERSION(1, 0, 0))
					.pEngineName(null) // stack.UTF8("No Engine")
					.engineVersion(VK_MAKE_VERSION(1, 0, 0))
					.apiVersion(VK_API_VERSION_1_3);

			final PointerBuffer requiredExtensions = getRequiredExtensions(stack);

			final VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
					.sType$Default()
					.pApplicationInfo(vkAppInto)
					.ppEnabledExtensionNames(requiredExtensions);
			if (enableValidationLayers) {
				final PointerBuffer pValidationLayers = stack.mallocPointer(validationLayers.size());
				for (int a = 0; a < validationLayers.size(); ++a) {
					pValidationLayers.put(a, stack.UTF8(validationLayers.get(a)));
				}

				createInfo.ppEnabledLayerNames(pValidationLayers);
			} else {
				createInfo.ppEnabledLayerNames(null);
			}

			final PointerBuffer pInstance = stack.mallocPointer(1);
			final int result = vkCreateInstance(createInfo, null, pInstance);
			final long instance = pInstance.get(0);
			if (result != VK_SUCCESS) {
				throw new AssertionError("Failed to create VkInstance: " + VkHelper.translateVulkanResult(result));
			}
			vkInstance = new VkInstance(instance, createInfo);
		}
	}

	private void pickPhysicalDevice() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			// get GPU count
			final IntBuffer pPhysicalDeviceCount = stack.mallocInt(1);
			int result = vkEnumeratePhysicalDevices(vkInstance, pPhysicalDeviceCount, null);
			if (result != VK_SUCCESS) {
				throw new AssertionError("Failed to get number of physical devices: " + VkHelper.translateVulkanResult(result));
			}
			final int deviceCount = pPhysicalDeviceCount.get(0);
			if (deviceCount == 0) {
				throw new AssertionError("Failed to find GPUs with Vulkan support!");
			}
			System.out.printf("🔷 Found %d physical devices with Vulkan support.\n", deviceCount);

			final PointerBuffer pPhysicalDevice = stack.mallocPointer(deviceCount);
			result = vkEnumeratePhysicalDevices(vkInstance, pPhysicalDeviceCount, pPhysicalDevice);

			final long physicalDevice = getBestPhysicalDevice(deviceCount, pPhysicalDevice);
			if (physicalDevice == 0) {
				throw new RuntimeException("Cannot find suitable physical device!");
			}
			if (result != VK_SUCCESS) {
				throw new AssertionError("Failed to get physical devices: " + VkHelper.translateVulkanResult(result));
			}
			this.vkPhysicalDevice = new VkPhysicalDevice(physicalDevice, vkInstance);
		}
	}

	private void createLogicalDevice() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			// using 2 different queues kinda breaks RTSS overlay for some reason
			final List<VkQueueInfo> queueIndices = findQueueIndices(vkPhysicalDevice, stack);

			// queue creation
			final FloatBuffer queuePriorities = stack.floats(0.0f);
			final VkDeviceQueueCreateInfo.Buffer queueCreateInfoBuffer = VkDeviceQueueCreateInfo.malloc(queueIndices.size(), stack);
			int index = 0;
			System.out.printf("🔷 Creating %d queues\n", queueIndices.size());
			for (VkQueueInfo queueInfo : queueIndices) {
				final VkDeviceQueueCreateInfo queueCreateInfo = VkDeviceQueueCreateInfo.calloc(stack)
						.sType$Default()
						.queueFamilyIndex(queueInfo.queueIndex)
						.pQueuePriorities(queuePriorities);
				queueCreateInfoBuffer.put(index, queueCreateInfo);
				System.out.printf("\tqueueFamilyIndex: %d\n", queueInfo.queueIndex);
				index++;
			}
			// queue creation

			final int requiredDeviceExtensionsCount = requiredDeviceExtensions.size();
			final PointerBuffer extensions = stack.mallocPointer(requiredDeviceExtensionsCount);
			for (String name : requiredDeviceExtensions) {
				extensions.put(stack.UTF8(name));
			}
			extensions.position(0);
//			extensions.flip();

			try (MemoryStack stack2 = MemoryStack.stackPush()) {
				// check feature support
				final VkPhysicalDeviceVulkan13Features deviceVulkan13Features = VkPhysicalDeviceVulkan13Features.calloc(stack2)
						.sType$Default();

				final VkPhysicalDeviceVulkan11Features deviceVulkan11Features = VkPhysicalDeviceVulkan11Features.calloc(stack2)
						.sType$Default()
						.pNext(deviceVulkan13Features.address());

				final VkPhysicalDeviceFeatures2 deviceFeatures2 = VkPhysicalDeviceFeatures2.calloc(stack2)
						.sType$Default()
						.pNext(deviceVulkan11Features.address());

				vkGetPhysicalDeviceFeatures2(vkPhysicalDevice, deviceFeatures2);

				final VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.calloc(stack2);
				vkGetPhysicalDeviceFeatures(vkPhysicalDevice, deviceFeatures);

				System.out.printf("shader draw parameters: %b\n", deviceVulkan11Features.shaderDrawParameters());
				System.out.printf("dynamic rendering: %b\n", deviceVulkan13Features.dynamicRendering());
				System.out.printf("synchronization2: %b\n", deviceVulkan13Features.synchronization2());
				System.out.printf("sampler anisotropy: %b\n", deviceFeatures.samplerAnisotropy());
				System.out.printf("multiDrawIndirect: %b\n", deviceFeatures.multiDrawIndirect());
			}

			final VkPhysicalDeviceVulkan13Features deviceVulkan13Features = VkPhysicalDeviceVulkan13Features.calloc(stack)
					.sType$Default();

			final VkPhysicalDeviceVulkan11Features deviceVulkan11Features = VkPhysicalDeviceVulkan11Features.calloc(stack)
					.sType$Default()
					.pNext(deviceVulkan13Features.address());

			final VkPhysicalDeviceFeatures2 deviceFeatures2 = VkPhysicalDeviceFeatures2.calloc(stack)
					.sType$Default()
					.pNext(deviceVulkan11Features.address());

			deviceVulkan11Features.shaderDrawParameters(true);
			deviceVulkan13Features.dynamicRendering(true);
			deviceVulkan13Features.dynamicRendering(true);
//			deviceVulkan13Features.synchronization2(true);
			deviceFeatures2.features().samplerAnisotropy(true);
			deviceFeatures2.features().multiDrawIndirect(true);

			// can be malloc() only if all fields are explicitly set
			// otherwise use calloc()
			// generally, if you allocate memory which will be immediately rewritten - use malloc
			// for structures that you will fill yourself - use calloc (unless you fill the whole structure)
			final VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
					.sType$Default()
					.pQueueCreateInfos(queueCreateInfoBuffer)
					.ppEnabledExtensionNames(extensions)
					.pEnabledFeatures(null) // deviceFeatures or null if pNext is not null
					.pNext(deviceFeatures2.address()); // deviceFeatures2
			if (enableValidationLayers) {
				final PointerBuffer pValidationLayers = stack.mallocPointer(validationLayers.size());
				for (int a = 0; a < validationLayers.size(); ++a) {
					pValidationLayers.put(a, stack.UTF8(validationLayers.get(a)));
				}
				createInfo.ppEnabledLayerNames(pValidationLayers);
			} else {
				createInfo.ppEnabledLayerNames(null);
			}

			final PointerBuffer pDevice = stack.mallocPointer(1);
			final int result = vkCreateDevice(vkPhysicalDevice, createInfo, null, pDevice);
			if (result != VK_SUCCESS) {
				throw new AssertionError("Failed to create logical device! Error code: " + VkHelper.translateVulkanResult(result));
			}
			this.vkLogicalDevice = new VkDevice(pDevice.get(0), vkPhysicalDevice, createInfo);
			System.out.printf("🔷 Logical device created: %d\n", vkLogicalDevice.address());

			// real queue creation
			vkQueueDataList.clear();
			for (VkQueueInfo queueInfo : queueIndices) {
				final PointerBuffer pQueue = stack.mallocPointer(1);
				vkGetDeviceQueue(vkLogicalDevice, queueInfo.queueIndex, 0, pQueue);
				final long vkQueueHandle = pQueue.get(0);
				final VkQueue vkQueue = new VkQueue(vkQueueHandle, vkLogicalDevice);
				System.out.printf("🔷 vkGetDeviceQueue queue type: %s | index: %d | present: %b | handle: %s\n", VkHelper.translateQueueBit(queueInfo.queueType), queueInfo.queueIndex, queueInfo.presentSupport, String.format("0x%08x", vkQueueHandle));
				this.vkQueueDataList.add(new VkQueue2(vkLogicalDevice, vkQueue, queueInfo));
			}
		}
	}

	public void enumerateDeviceExtensions() {
		// VK_EXT_descriptor_buffer
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final IntBuffer ip = stack.mallocInt(1);
			vkEnumerateInstanceExtensionProperties((String) null, ip, null);

			if (ip.get(0) != 0) {
				final VkExtensionProperties.Buffer instance_extensions = VkExtensionProperties.malloc(ip.get(0), stack);
				vkEnumerateInstanceExtensionProperties((String) null, ip, instance_extensions);

				System.out.println("Supported extensions:");
				for (int i = 0; i < ip.get(0); i++) {
					instance_extensions.position(i);
					System.out.println(instance_extensions.extensionNameString());
				}
			}
		}
	}

	public VkQueue2 getQueue(int type, boolean getWithPresent) {
		for (VkQueue2 queue2 : vkQueueDataList) {
			if ((queue2.getQueueInfo().queueType & type) == type) {
				if (getWithPresent) {
					if (queue2.getQueueInfo().presentSupport) {
						return queue2;
					} else {
						return null;
					}
				}
				return queue2;
			}
		}
		return null;
	}

	public VkQueue getVkGraphicsQueue() {
		for (VkQueue2 queue2 : vkQueueDataList) {
			if ((queue2.getQueueInfo().queueType & VK_QUEUE_GRAPHICS_BIT) == VK_QUEUE_GRAPHICS_BIT) {
				return queue2.getQueue();
			}
		}
		return null;
	}

	public VkQueue getVkPresentQueue() {
		for (VkQueue2 queue2 : vkQueueDataList) {
			if (queue2.getQueueInfo().presentSupport) {
				return queue2.getQueue();
			}
		}
		return null;
	}

	public VkQueue getVkTransferQueue() {
		for (VkQueue2 queue2 : vkQueueDataList) {
			if ((queue2.getQueueInfo().queueType & VK_QUEUE_TRANSFER_BIT) == VK_QUEUE_TRANSFER_BIT) {
				return queue2.getQueue();
			}
		}
		return null;
	}

	public record VkQueueInfo(int queueIndex, boolean presentSupport, int queueType) {
		public static VkQueueInfo getByType(List<VkQueueInfo> queueInfoList, int queueType) {
			for (VkQueueInfo queueInfo : queueInfoList) {
				if ((queueInfo.queueType & queueType) == queueType) {
					return queueInfo;
				}
			}
			return null;
		}

		public static VkQueueInfo getWithPresent(List<VkQueueInfo> queueInfoList, int queueType) {
			for (VkQueueInfo queueInfo : queueInfoList) {
				if ((queueInfo.queueType & queueType) == queueType && queueInfo.presentSupport) {
					return queueInfo;
				}
			}
			return null;
		}

		public static VkQueueInfo getWithPresent(List<VkQueueInfo> queueInfoList) {
			for (VkQueueInfo queueInfo : queueInfoList) {
				if (queueInfo.presentSupport) {
					return queueInfo;
				}
			}
			return null;
		}

		public static VkQueueInfo getByIndex(List<VkQueueInfo> queueInfoList, int index) {
			for (VkQueueInfo queueInfo : queueInfoList) {
				if (queueInfo.queueIndex == index) {
					return queueInfo;
				}
			}
			return null;
		}
	}

	private List<VkQueueInfo> findQueueIndices(VkPhysicalDevice device, MemoryStack stack) {
		final IntBuffer pQueueFamilyCount = stack.mallocInt(1);
		vkGetPhysicalDeviceQueueFamilyProperties(device, pQueueFamilyCount, null);
		final int queueFamilyCount = pQueueFamilyCount.get(0);
		final VkQueueFamilyProperties.Buffer vkQueueFamilyPropertiesBuffer = VkQueueFamilyProperties.malloc(queueFamilyCount, stack);
		vkGetPhysicalDeviceQueueFamilyProperties(device, pQueueFamilyCount, vkQueueFamilyPropertiesBuffer);

		final List<VkQueueInfo> queueInfoList = new ArrayList<>();
//		int graphicQueueIndex = -1;
//		int presentQueueIndex = -1;
//		int transferQueueIndex = -1;
		System.out.printf("🔷 vkGetPhysicalDeviceQueueFamilyProperties queueFamilyCount: %d\n", queueFamilyCount);
		final int[] presentSupportRef = new int[1];
		for (int index = 0; index < queueFamilyCount; ++index) {
			final VkQueueFamilyProperties queueFamilyProperties = vkQueueFamilyPropertiesBuffer.get(index);
			final int flags = queueFamilyProperties.queueFlags();
			vkGetPhysicalDeviceSurfaceSupportKHR(device, index, vkSurface, presentSupportRef);
			final int presentSupport = presentSupportRef[0];

//			if ((flags & VK_QUEUE_GRAPHICS_BIT) != 0) {
//				graphicQueueIndex = index;
//			}
//			if (presentSupport == 1) {
//				presentQueueIndex = index;
//			}

			if ((flags & VK_QUEUE_GRAPHICS_BIT) == VK_QUEUE_GRAPHICS_BIT) {
				if (VkQueueInfo.getByIndex(queueInfoList, index) == null) {
					queueInfoList.add(new VkQueueInfo(index, false, flags));
				}
			}

			if ((flags & VK_QUEUE_GRAPHICS_BIT) != VK_QUEUE_GRAPHICS_BIT && presentSupport == 1) {
				if (VkQueueInfo.getByIndex(queueInfoList, index) == null) {
					queueInfoList.add(new VkQueueInfo(index, true, flags));
				}
			}

			if ((flags & VK_QUEUE_TRANSFER_BIT) == VK_QUEUE_TRANSFER_BIT) {
				if (VkQueueInfo.getByIndex(queueInfoList, index) == null) {
					queueInfoList.add(new VkQueueInfo(index, false, flags));
				}
			}

//			if (overlappingQueues) {
//				if ((flags & VK_QUEUE_GRAPHICS_BIT) == VK_QUEUE_GRAPHICS_BIT && presentSupport == 1 && graphicQueueIndex == -1) {
//					graphicQueueIndex = index;
//				}
//
//				if (presentSupport == 1 && presentQueueIndex == -1) {
//					presentQueueIndex = index;
//				}
//
//				if ((flags & VK_QUEUE_TRANSFER_BIT) == VK_QUEUE_TRANSFER_BIT && transferQueueIndex == -1) {
//					transferQueueIndex = index;
//				}
//			} else {
//				if ((flags & VK_QUEUE_GRAPHICS_BIT) == VK_QUEUE_GRAPHICS_BIT && graphicQueueIndex == -1) {
//					graphicQueueIndex = index;
//				}
//
//				if ((flags & VK_QUEUE_GRAPHICS_BIT) != VK_QUEUE_GRAPHICS_BIT && presentSupport == 1 && graphicQueueIndex != index) {
//					presentQueueIndex = index;
//				}
//
//				if ((flags & VK_QUEUE_TRANSFER_BIT) == VK_QUEUE_TRANSFER_BIT && graphicQueueIndex != index && transferQueueIndex != index) {
//					transferQueueIndex = index;
//				}
//			}

			System.out.printf("🔷 Queue %d presentSupport %d flags %d - %s\n", index, presentSupport, flags, VkHelper.translateQueueBit(flags));
		}
		System.out.printf("🔷 Selected queues:\n");
		for (VkQueueInfo queueInfo : queueInfoList) {
			System.out.printf("\tindex: %d | present support: %b | type: %s\n", queueInfo.queueIndex, queueInfo.presentSupport, VkHelper.translateQueueBit(queueInfo.queueType));
		}
		return queueInfoList;
	}

	private PointerBuffer getRequiredExtensions(MemoryStack stack) {
		final PointerBuffer requiredExtensions = glfwGetRequiredInstanceExtensions();
		if (requiredExtensions == null) {
			throw new AssertionError("Failed to get required instance extensions!");
		}
		final PointerBuffer ppEnabledExtensionNames = stack.mallocPointer(requiredExtensions.remaining());
		ppEnabledExtensionNames.put(requiredExtensions);
		ppEnabledExtensionNames.flip();

		for (int index = 0; index < requiredExtensions.capacity(); ++index) {
			long pointer = requiredExtensions.get(index);
			String string = memUTF8(pointer);
			System.out.printf("Required extension [%d] %s\n", index, string);
		}

		return ppEnabledExtensionNames;
	}

	private long getBestPhysicalDevice(int deviceCount, PointerBuffer pPhysicalDevice) {
		final List<Pair<Integer, Long>> candidates = new ArrayList<>();
		for (int a = 0; a < deviceCount; ++a) {
			long physicalDevice = pPhysicalDevice.get();
			int score = rateDeviceSuitability(physicalDevice);
			candidates.add(new Pair<>(score, physicalDevice));
		}
		int maxScore = Integer.MIN_VALUE;
		long bestDevice = 0;
		for (Pair<Integer, Long> pair : candidates) {
			if (pair.left > maxScore && pair.left != 0) {
				maxScore = pair.left;
				bestDevice = pair.right;
			}
		}
		return bestDevice;
	}

	private int rateDeviceSuitability(long physicalDevice) {
		final VkPhysicalDevice device = new VkPhysicalDevice(physicalDevice, vkInstance);
		int score = 0;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkPhysicalDeviceProperties deviceProperties = VkPhysicalDeviceProperties.malloc(stack);
			vkGetPhysicalDeviceProperties(device, deviceProperties);
			final VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.malloc(stack);
			vkGetPhysicalDeviceFeatures(device, deviceFeatures);

			if (deviceProperties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
				score += 5000;
			} else if (deviceProperties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU) {
				score += 100;
			}

			score += deviceProperties.limits().maxImageDimension2D();

			if (!deviceFeatures.geometryShader()) {
				score = 0;
				System.err.print("Bad Device. No geometry shader support found.\n");
			}

			final List<VkQueueInfo> queueIndices = findQueueIndices(device, stack);
			final VkQueueInfo graphicsQueueInfo = VkQueueInfo.getByType(queueIndices, VK_QUEUE_GRAPHICS_BIT);
			final VkQueueInfo presentQueueInfo = VkQueueInfo.getWithPresent(queueIndices);
			final VkQueueInfo transferQueueInfo = VkQueueInfo.getByType(queueIndices, VK_QUEUE_TRANSFER_BIT);
			if (graphicsQueueInfo == null) {
				score = 0;
				System.err.print("Bad Device. Doesn't have queue which has 'VK_QUEUE_GRAPHICS_BIT' bit!\n");
			}
			if (presentQueueInfo == null) {
				score = 0;
				System.err.print("Bad Device. Doesn't have present queue!\n");
			}

			// check if device supports important extensions
			final boolean extensionsSupported = checkDeviceExtensionSupport(device, stack);
			if (!extensionsSupported) {
				score = 0;
				System.err.print("Bad. Device doesn't support all required extensions!\n");
			}
			if (extensionsSupported) {
				final SwapChainSupportDetails swapChainSupport = querySwapChainSupport(device, stack);
				final boolean swapChainSupportFound = swapChainSupport.surfaceFormatCount != 0 && swapChainSupport.presentModeCount != 0;
				if (!swapChainSupportFound) {
					score = 0;
					System.err.print("Bad. Device doesn't have any surface formats and present modes\n");
				}
				swapChainSupport.print();
			}
			if (!deviceFeatures.samplerAnisotropy()) {
				score = 0;
				System.err.print("Bad. Device doesn't support anisotropy!\n");
			}

			printDeviceProperties(deviceProperties);
			printDeviceFeatures(deviceFeatures);

			System.out.printf("Score for device %s is: %d \n", deviceProperties.deviceNameString(), score);
			return score;
		}
	}

	private boolean checkDeviceExtensionSupport(VkPhysicalDevice device, MemoryStack stack) {
		final int[] extensionCountRef = new int[1];
		vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCountRef, null);
		final int extensionCount = extensionCountRef[0];

		final VkExtensionProperties.Buffer vkExtensionProperties = VkExtensionProperties.malloc(extensionCount, stack);
		vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCountRef, vkExtensionProperties);

		int found = 0;
		for (int a = 0; a < extensionCount; ++a) {
			VkExtensionProperties extensionProperties = vkExtensionProperties.get(a);
			if (requiredDeviceExtensions.contains(extensionProperties.extensionNameString())) {
				found++;
			}
		}

		return found == requiredDeviceExtensions.size();
	}

	public SwapChainSupportDetails querySwapChainSupport(VkPhysicalDevice device, MemoryStack stack) {
		final SwapChainSupportDetails details = new SwapChainSupportDetails();

		details.capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
		vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, vkSurface, details.capabilities);
		final IntBuffer formatCountRef = stack.mallocInt(1);
		vkGetPhysicalDeviceSurfaceFormatsKHR(device, vkSurface, formatCountRef, null);
		final int formatCount = formatCountRef.get(0);

		if (formatCount != 0) {
			details.surfaceFormats = VkSurfaceFormatKHR.malloc(formatCount, stack);
			details.surfaceFormatCount = formatCount;
			vkGetPhysicalDeviceSurfaceFormatsKHR(device, vkSurface, formatCountRef, details.surfaceFormats);
		}

		final int[] presentModeCountRef = new int[1];
		vkGetPhysicalDeviceSurfacePresentModesKHR(device, vkSurface, presentModeCountRef, null);
		final int presentModeCount = presentModeCountRef[0];

		if (presentModeCount != 0) {
			details.presentModes = new int[presentModeCount];
			details.presentModeCount = presentModeCount;
			vkGetPhysicalDeviceSurfacePresentModesKHR(device, vkSurface, presentModeCountRef, details.presentModes);
		}

		return details;
	}

	private boolean checkValidationLayerSupport() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final IntBuffer pLayerCount = stack.mallocInt(1);
			vkEnumerateInstanceLayerProperties(pLayerCount, null);
			final int layerCount = pLayerCount.get(0);

			final VkLayerProperties.Buffer availableLayers = VkLayerProperties.malloc(layerCount, stack);
			vkEnumerateInstanceLayerProperties(pLayerCount, availableLayers);

			boolean layerFound = false;
			for (String layerName : validationLayers) {
				for (VkLayerProperties layerProperties : availableLayers) {
					System.out.printf("Layer: %s\n", layerProperties.layerNameString());
					if (layerName.equals(layerProperties.layerNameString())) {
						layerFound = true;
						break;
					}
				}
			}
			return layerFound;
		}
	}

	private void printDeviceProperties(VkPhysicalDeviceProperties deviceProperties) {
		System.out.println("🔷 Device Properties");
		System.out.printf("\tDevice ID: %d \n", deviceProperties.deviceID());
		System.out.printf("\tDevice name string: %s \n", deviceProperties.deviceNameString());
		System.out.printf("\tDevice type: %s \n", VkHelper.translatePhysicalDeviceType(deviceProperties.deviceType()));
		System.out.printf("\tVendor ID: %d \n", deviceProperties.vendorID());

		final int apiVersion = deviceProperties.apiVersion();
		System.out.printf("\tVulkan API version: %d.%d.%d \n", VK_API_VERSION_MAJOR(apiVersion), VK_API_VERSION_MINOR(apiVersion), VK_API_VERSION_PATCH(apiVersion));
		final int driverVersion = deviceProperties.driverVersion();
		System.out.printf("\tVulkan Driver version: %d.%d.%d \n", VK_VERSION_MAJOR(driverVersion), VK_VERSION_MINOR(driverVersion), VK_VERSION_PATCH(driverVersion));

		System.out.printf("\tMax image dimension 2d: %d \n", deviceProperties.limits().maxImageDimension2D());
		System.out.printf("\tMax framebuffer layers: %d \n", deviceProperties.limits().maxFramebufferLayers());
		System.out.printf("\tMax framebuffer color sample counts: %d \n", deviceProperties.limits().framebufferColorSampleCounts());
		System.out.printf("\tMax framebuffer depth sample counts: %d \n", deviceProperties.limits().framebufferDepthSampleCounts());
		System.out.printf("\tMax framebuffer no attachment sample counts: %d \n", deviceProperties.limits().framebufferNoAttachmentsSampleCounts());
		System.out.printf("\tMax framebuffer stencil sample counts: %d \n", deviceProperties.limits().framebufferStencilSampleCounts());
		System.out.printf("\tMax viewports: %d \n", deviceProperties.limits().maxViewports());
		System.out.printf("\tMax image layers arrays: %d \n", deviceProperties.limits().maxImageArrayLayers());
		System.out.printf("\tMax vertex input attributes: %d \n", deviceProperties.limits().maxVertexInputAttributes());
		System.out.printf("\tMax vertex input bindings: %d \n", deviceProperties.limits().maxVertexInputBindings());
		System.out.printf("\tMax fragment input components: %d \n", deviceProperties.limits().maxFragmentInputComponents());
		System.out.printf("\tMax fragment output attachments: %d \n", deviceProperties.limits().maxFragmentOutputAttachments());

		final IntBuffer computeGroupCount = deviceProperties.limits().maxComputeWorkGroupCount();
		final int computeGroupCountRemaining = computeGroupCount.remaining();
		System.out.print("\tMax compute work group count: ");
		for (int a = 0; a < computeGroupCountRemaining; ++a) {
			System.out.printf("%d ", computeGroupCount.get());
		}
		System.out.print("\n");

		final IntBuffer computeGroupSize = deviceProperties.limits().maxComputeWorkGroupSize();
		final int computeGroupSizeRemaining = computeGroupSize.remaining();
		System.out.print("\tMax compute work group size: ");
		for (int a = 0; a < computeGroupSizeRemaining; ++a) {
			System.out.printf("%d ", computeGroupSize.get());
		}
		System.out.print("\n");
		System.out.printf("\tMax compute shared memory size: %d \n", deviceProperties.limits().maxComputeSharedMemorySize());
		System.out.printf("\tMax compute work group invocations: %d \n", deviceProperties.limits().maxComputeWorkGroupInvocations());
	}

	private void printDeviceFeatures(VkPhysicalDeviceFeatures deviceFeatures) {
		System.out.println("🔷 Device Features");
		System.out.printf("\tMultiViewport: %b\n", deviceFeatures.multiViewport());
		System.out.printf("\tTessellationShader: %b\n", deviceFeatures.tessellationShader());
		System.out.printf("\tGeometryShader: %b\n", deviceFeatures.geometryShader());
		System.out.printf("\tMultiDrawIndirect: %b\n", deviceFeatures.multiDrawIndirect());
		System.out.printf("\tDrawIndirectFirstInstance: %b\n", deviceFeatures.drawIndirectFirstInstance());
		System.out.printf("\tShaderFloat64: %b\n", deviceFeatures.shaderFloat64());
		System.out.printf("\tShaderInt16: %b\n", deviceFeatures.shaderInt16());
		System.out.printf("\tShaderInt64: %b\n", deviceFeatures.shaderInt64());
	}

	public void destroy() {
		vkDestroyDevice(vkLogicalDevice, null);
		vkDestroySurfaceKHR(vkInstance, vkSurface, null);
		vkDestroyInstance(vkInstance, null);
	}
}
