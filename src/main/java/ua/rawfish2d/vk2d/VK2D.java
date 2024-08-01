package ua.rawfish2d.vk2d;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import ua.rawfish2d.Bullet;
import ua.rawfish2d.BulletTest;
import ua.rawfish2d.vk2d.attrib.AttribFormat;
import ua.rawfish2d.vk2d.attrib.BufferLayout;
import ua.rawfish2d.vk2d.init.SwapChainSupportDetails;
import ua.rawfish2d.vk2d.init.VkDeviceInstance;
import ua.rawfish2d.vk2d.init.VkQueue2;
import ua.rawfish2d.vk2d.utils.ExecutionTimer;
import ua.rawfish2d.vk2d.utils.FPSCounter;
import ua.rawfish2d.vk2d.utils.MathUtils;
import ua.rawfish2d.vk2d.utils.TimeHelper;
import ua.rawfish2d.vk2d.vkutils.VkHelper;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class VK2D {
	private final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL;
	private WindowVK windowVK;
	private int windowWidth = 1024;
	private int windowHeight = 768;
	// Vulkan
	private VkDeviceInstance deviceInstance;
	private long vkSurface;
	private VkDevice vkLogicalDevice;
	private VkPhysicalDevice vkPhysicalDevice;
	private VkQueue vkGraphicsQueue;
	private VkQueue vkPresentQueue;
	private VkQueue vkTransferQueue;
	private long vkSwapchainKHR;
	private int vkSwapChainImageFormat;
	private VkExtent2D vkExtent2D = null;
	private LongBuffer vkSwapChainImages;
	private long[] vkSwapChainImageViews;
	private long vkRenderPass;
	private VkGraphicsPipeline vkGraphicsPipeline;
	private final List<Framebuffer> framebufferList = new ArrayList<>();
	private final boolean enableValidationLayers = false;
	private boolean minimized = false;
	private boolean vsync = true;
	private boolean shouldRecreateSwapChain = false;
	private final int swapChainImageCount = 3;
	private VkBuffer vkVertexBuffer;
	private VkBuffer vkIndexBuffer;
	private VkBuffer vkSSBO;
	private IndirectBuffer vkIndirectBuffer;
	private final List<VkBuffer> uniformBuffers = new ArrayList<>();
	private Descriptor descriptor;
	private VkTexture textureStuff;
	private BulletTest bulletTest;
	private final int bulletsCount = 20000;
	public static final boolean useDynamicRendering = true;

	public VK2D() {
		initWindow();

//		new Thread(() -> {
		initVulkan();
		mainLoop();
		cleanup();
//		}).start();

//		while (!windowVK.shouldClose()) {
//			glfwWaitEvents();
//		}
	}

	private void mainLoop() {
		windowVK.showWindow();

		final FPSCounter fpsCounter = new FPSCounter();
		while (!windowVK.shouldClose()) {
			glfwPollEvents();
			fpsCounter.pre();
			render();
			fpsCounter.post();
		}

		vkDeviceWaitIdle(vkLogicalDevice);
//		try {
//			bufferedOutputStream.flush();
//			bufferedOutputStream.close();
//		} catch (IOException e) {
//			throw new RuntimeException(e);
//		}
	}

	private void render() {
//		drawFrame();
		drawFrameNew();
	}

	private int currentFrame = 0;
	private final IntBuffer pCurrentFrameIndex = memAllocInt(1);
	private final ExecutionTimer clearToSwapTimer = new ExecutionTimer();
	private final ExecutionTimer drawTimer = new ExecutionTimer();
	private final ExecutionTimer updateTimer = new ExecutionTimer();
	private final TimeHelper printTimer = new TimeHelper();
	private final ExecutionTimer acquireImageTimer = new ExecutionTimer();
	private final ExecutionTimer makeCommandsTimer = new ExecutionTimer();
	private final ExecutionTimer queueSubmitTimer = new ExecutionTimer();
	private final ExecutionTimer queuePresentTimer = new ExecutionTimer();
	private VkCommandBuffer uploadCommandBuffer;
	private final List<DrawCommand> drawCommands = new ArrayList<>();
//	private final ExecutorService es = Executors.newFixedThreadPool(1);
//	private int counter = 0;
//	private static final BufferedOutputStream bufferedOutputStream;
//
//	static {
//		try {
//			bufferedOutputStream = new BufferedOutputStream(new FileOutputStream("log.log"), 65536);
//		} catch (FileNotFoundException e) {
//			throw new RuntimeException(e);
//		}
//	}

//	public static void log_perf(String message, long delta) {
//		log("%s: %.3f ms\n", message, (float) delta / 1_000_000f);
//	}

//	private static void log(String format, Object... args) {
//		try {
//			bufferedOutputStream.write(String.format(Locale.US, format, args).getBytes(Charset.defaultCharset()));
//		} catch (IOException e) {
//			throw new RuntimeException(e);
//		}
//	}

	public int acquireImage() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			acquireImageTimer.preCall();
			final Framebuffer framebuffer = framebufferList.get(currentFrame);

			if (shouldRecreateSwapChain) {
				recreateSwapChain(stack);
			}

			// takes 1.3 - 1.5 ms
//			long time = System.nanoTime();
			vkWaitForFences(vkLogicalDevice, framebuffer.getVkInFlightFence(), true, UINT64_MAX);
//			log_perf("vkWaitForFences", System.nanoTime() - time);
//			while (vkWaitForFences(vkLogicalDevice, framebuffer.getVkInFlightFence(), true, 100_000) == VK_TIMEOUT) {
//				;
//			}

			// takes 0.05 - 0.1 ms
//			time = System.nanoTime();
			final int acquireNextImageKHRResult = vkAcquireNextImageKHR(vkLogicalDevice, vkSwapchainKHR, UINT64_MAX,
					framebuffer.getVkImageAvailableSemaphore(), VK_NULL_HANDLE, pCurrentFrameIndex);
			final int imageIndex = pCurrentFrameIndex.get(0);
//			log_perf("vkAcquireNextImageKHR", System.nanoTime() - time);

//			System.out.printf("currentFrame: %d imageIndex: %d\n", currentFrame, imageIndex);

			if (acquireNextImageKHRResult == VK_ERROR_OUT_OF_DATE_KHR) {
				recreateSwapChain(stack);
				return -1;
			} else if (acquireNextImageKHRResult != VK_SUCCESS && acquireNextImageKHRResult != VK_SUBOPTIMAL_KHR) {
				throw new RuntimeException("Failed to acquire swap chain image!");
			}
			vkResetFences(vkLogicalDevice, framebuffer.getVkInFlightFence());
			acquireImageTimer.postCall();
			return imageIndex;
		}
	}

	private int frameCounter = 0;

	private void drawFrameNew() {
		clearToSwapTimer.preCall();

		final boolean updated = bulletTest.shouldUpdate();
		if (updated) {
			bulletTest.updateBulletPos();
		}
//		VkHelper.sleepNanos(1_300_000);
		int imageIndex = acquireImage();
		if (imageIndex == -1) {
			return;
		}

		makeCommandsTimer.preCall();
		final DrawCommand drawCommand = drawCommands.get(currentFrame);
		final VkCommandBuffer commandBuffer;
		final VkSubmitInfo submitInfo;
		if (updated) {
			drawCommand.setWindowWidth(windowWidth);
			drawCommand.setWindowHeight(windowHeight);
			Bullet.BulletInfo.windowWidth = windowWidth;
			Bullet.BulletInfo.windowHeight = windowHeight;
			commandBuffer = drawCommand.getVkDrawAndUploadCommandBuffer();

			if (!drawCommand.isDrawAndUploadCommandCached()) {
				VkHelper.beginCommandBuffer(commandBuffer);
				drawCommand.recordDrawCommands(imageIndex, commandBuffer, vkSwapChainImages, vkSwapChainImageViews, bulletTest);
				VkHelper.endCommandBuffer(commandBuffer);
				drawCommand.setDrawAndUploadCommandCached(true);
			}
			submitInfo = drawCommand.getUploadDrawSubmitInfo(commandBuffer);
		} else {
			commandBuffer = drawCommand.getVkDrawCommandBuffer();

			if (!drawCommand.isDrawCommandCached()) {
				VkHelper.beginCommandBuffer(commandBuffer);
				drawCommand.recordDrawCommands(imageIndex, commandBuffer, vkSwapChainImages, vkSwapChainImageViews, null);
				VkHelper.endCommandBuffer(commandBuffer);
				drawCommand.setDrawCommandCached(true);
			}
			submitInfo = drawCommand.getDrawSubmitInfo(commandBuffer);
		}
		makeCommandsTimer.postCall();

		// submit
		int result = 0;
		queueSubmitTimer.preCall();
		result = vkQueueSubmit(vkGraphicsQueue, submitInfo, drawCommand.getFramebuffer().getVkInFlightFence());
		if (result != VK_SUCCESS) {
			throw new RuntimeException("Failed to submit command buffer! " + VkHelper.translateVulkanResult(result));
		}
		queueSubmitTimer.postCall();
//		log_perf("vkQueueSubmit (DRAW)", System.nanoTime() - time);
//		time = System.nanoTime();

		queuePresentTimer.preCall();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			// Present the swap chain image
			final LongBuffer pSwapChains = stack.longs(vkSwapchainKHR);
			final VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
					.sType$Default()
					.pWaitSemaphores(stack.longs(drawCommand.getFramebuffer().getVkRenderFinishedSemaphore()))
					.swapchainCount(1)
					.pSwapchains(pSwapChains)
					.pImageIndices(pCurrentFrameIndex)
					.pResults(null);

			// takes 0.25 - 0.27 ms
			final int queuePresentKHRResult = nvkQueuePresentKHR(vkPresentQueue, presentInfo.address());
			if (queuePresentKHRResult == VK_ERROR_OUT_OF_DATE_KHR || queuePresentKHRResult == VK_SUBOPTIMAL_KHR) {
				recreateSwapChain(stack);
			} else if (queuePresentKHRResult != VK_SUCCESS) {
				throw new RuntimeException("Failed to present swap chain image!");
			}

			currentFrame = (currentFrame + 1) % swapChainImageCount;
			drawTimer.postCall();
//			log_perf("nvkQueuePresentKHR", System.nanoTime() - time);
		}

		queuePresentTimer.postCall();
		clearToSwapTimer.postCall();
		frameCounter++;

//		vkDeviceWaitIdle(vkLogicalDevice);

//		if (frameCounter >= 9) {
//			System.exit(0);
//		}

//		if (printTimer.hasReachedMilli(1000)) {
//			printTimer.reset();
//			System.out.printf("""
//							from vkWaitForFences to nvkQueuePresentKHR: %s ms avg
//							acquireImageTimer: %s ms avg
//							makeCommands: %s ms avg
//							vkQueueSubmit: %s ms avg
//							nvkQueuePresentKHR: %s ms avg
//							""",
//					clearToSwapTimer.getFormatedTime(),
//					acquireImageTimer.getFormatedTime(),
//					makeCommandsTimer.getFormatedTime(),
//					queueSubmitTimer.getFormatedTime(),
//					queuePresentTimer.getFormatedTime()
//			);
//		}
//		log("\tend of frame %d\n", counter);
//		counter++;
	}

	//	private boolean dontUpload = false;
	private long vkCommandPool;

//	private void drawFrame() {
//		final Framebuffer framebuffer = framebufferList.get(currentFrame);
//		final int imageIndex;
//		clearToSwapTimer.preCall();
//		try (MemoryStack stack = MemoryStack.stackPush()) {
//			if (shouldRecreateSwapChain) {
//				recreateSwapChain(stack);
//			}
//
//			// Wait for the previous frame to finish
//			// takes 1.3 - 1.5 ms
//			vkWaitForFences(vkLogicalDevice, framebuffer.getVkInFlightFence(), true, UINT64_MAX);
//
//			// Acquire an image from the swap chain
//			// takes 0.05 - 0.1 ms
//			final int acquireNextImageKHRResult = vkAcquireNextImageKHR(vkLogicalDevice, vkSwapchainKHR, UINT64_MAX,
//					framebuffer.getVkImageAvailableSemaphore(), VK_NULL_HANDLE, pCurrentFrameIndex);
//			imageIndex = pCurrentFrameIndex.get(0);
//
//			if (acquireNextImageKHRResult == VK_ERROR_OUT_OF_DATE_KHR) {
//				recreateSwapChain(stack);
//				return;
//			} else if (acquireNextImageKHRResult != VK_SUCCESS && acquireNextImageKHRResult != VK_SUBOPTIMAL_KHR) {
//				throw new RuntimeException("Failed to acquire swap chain image!");
//			}
//			vkResetFences(vkLogicalDevice, framebuffer.getVkInFlightFence());
//		}
//		// Record a command buffer which draws the scene onto that image
//		// takes 0.1 ms
//		if (!dontUpload) {
//			vkResetCommandBuffer(framebuffer.getVkCommandBuffer(), 0);
//			recordCommandBuffer(framebuffer.getVkCommandBuffer(), imageIndex);
//		}
//		if (uploadCommandBuffer == null) {
//			uploadCommandBuffer = VkHelper.createCommandBuffer(vkLogicalDevice, vkCommandPool);
//		}
//
//		try (MemoryStack stack = MemoryStack.stackPush()) {
//			// Submit the recorded command buffer
//			final LongBuffer pWaitSemaphores = stack.longs(framebuffer.getVkImageAvailableSemaphore());
//			final IntBuffer pWaitStages = stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
////			final PointerBuffer pCommandBuffers = stack.pointers(framebuffer.getVkCommandBuffer());
//			final PointerBuffer pCommandBuffers;
//
//			if (bulletTest.shouldUpdate() && uploadCommandBuffer != null) {
//				updateTimer.preCall();
//				vkResetCommandBuffer(uploadCommandBuffer, 0);
//				VkHelper.beginCommandBuffer(uploadCommandBuffer);
//				bulletTest.updateBulletPos();
//				bulletTest.uploadBulletPos(uploadCommandBuffer);
//
//				final VkBuffer uniformBuffer = uniformBuffers.get(imageIndex);
//				final ByteBuffer buffer = uniformBuffer.getStagingBuffer();
//				buffer.putFloat(0, windowWidth);
//				buffer.putFloat(4, windowHeight);
//				uniformBuffer.uploadFromStagingBuffer(uploadCommandBuffer);
//				VkHelper.endCommandBuffer(uploadCommandBuffer);
//				updateTimer.postCall();
//
//				pCommandBuffers = stack.pointers(uploadCommandBuffer, framebuffer.getVkCommandBuffer());
//			} else {
//				pCommandBuffers = stack.pointers(framebuffer.getVkCommandBuffer());
//			}
//			drawTimer.preCall();
//
//			final LongBuffer pSignalSemaphores = stack.longs(framebuffer.getVkRenderFinishedSemaphore());
//
//			final VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
//					.sType$Default()
//					.waitSemaphoreCount(1)
//					.pWaitSemaphores(pWaitSemaphores)
//					.pWaitDstStageMask(pWaitStages)
//					.pCommandBuffers(pCommandBuffers)
//					.pSignalSemaphores(pSignalSemaphores);
//
//			// takes 0.05 ms
//			final int result = vkQueueSubmit(vkGraphicsQueue, submitInfo, framebuffer.getVkInFlightFence());
//			if (result != VK_SUCCESS) {
//				throw new RuntimeException("Failed to submit draw command buffer! " + VkHelper.translateVulkanResult(result));
//			}
//
//			// Present the swap chain image
//			final LongBuffer pSwapChains = stack.longs(vkSwapchainKHR);
//			final VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
//					.sType$Default()
//					.pWaitSemaphores(pSignalSemaphores)
//					.swapchainCount(1)
//					.pSwapchains(pSwapChains)
//					.pImageIndices(pCurrentFrameIndex)
//					.pResults(null);
//
//			// takes 0.25 - 0.27 ms
//			final int queuePresentKHRResult = nvkQueuePresentKHR(vkPresentQueue, presentInfo.address());
//			if (queuePresentKHRResult == VK_ERROR_OUT_OF_DATE_KHR || queuePresentKHRResult == VK_SUBOPTIMAL_KHR) {
//				recreateSwapChain(stack);
//			} else if (queuePresentKHRResult != VK_SUCCESS) {
//				throw new RuntimeException("Failed to present swap chain image!");
//			}
//
//			if (currentFrame == swapChainImageCount - 1) {
//				dontUpload = true;
//			}
//			currentFrame = (currentFrame + 1) % swapChainImageCount;
//			drawTimer.postCall();
//		}
//
//		clearToSwapTimer.postCall();
//		if (printTimer.hasReachedMilli(1000)) {
//			printTimer.reset();
//			System.out.printf("""
//							from vkWaitForFences to nvkQueuePresentKHR: %s ms avg
//							vkResetCommandBuffer to vkEndCommandBuffer (update uniforms and vertex buffers): %s ms avg
//							vkQueueSubmit and nvkQueuePresentKHR: %s ms avg
//							""",
//					clearToSwapTimer.getFormatedTime(),
//					updateTimer.getFormatedTime(),
//					drawTimer.getFormatedTime()
//			);
//		}
//	}

	public void initVulkan() {
		// setup
		deviceInstance = new VkDeviceInstance();
		deviceInstance.enumerateDeviceExtensions();
		deviceInstance.create(windowVK.getHandle(), enableValidationLayers, false);
		this.vkPhysicalDevice = deviceInstance.getVkPhysicalDevice();
		this.vkLogicalDevice = deviceInstance.getVkLogicalDevice();
		this.vkGraphicsQueue = deviceInstance.getVkGraphicsQueue();
		this.vkPresentQueue = deviceInstance.getVkPresentQueue();
		this.vkTransferQueue = deviceInstance.getVkTransferQueue();
		this.vkSurface = deviceInstance.getVkSurface();
		//
		VkHelper.checkImageFormatSupport(vkPhysicalDevice);
		// setup and window resize (not yet though)
		createSwapChain();
		createImageViews();
		//
		if (!useDynamicRendering) {
			createRenderPass();
		}
		createFramebuffers();

//		createCommandPool(vkLogicalDevice, deviceInstance.getQueueIndices());

		final String filename = "assets\\textures\\bullets0.png";
		textureStuff = new VkTexture();
		textureStuff.loadAndCreateImage(filename, vkLogicalDevice, vkPhysicalDevice, 0, vkGraphicsQueue);

		final int primitivesCount = 10;
		final AttribFormat vertexBufferAttribFormat = new AttribFormat()
				.add(0, 0, VK_FORMAT_R32G32_SFLOAT)
				.add(0, 1, VK_FORMAT_R32G32_SFLOAT);
		vertexBufferAttribFormat.setBufferLayout(BufferLayout.SEQUENTIAL);
		vertexBufferAttribFormat.setPrimitiveCount(primitivesCount);
		vertexBufferAttribFormat.setVerticesPerPrimitive(4);
		vkVertexBuffer = createVertexBuffer(vertexBufferAttribFormat, primitivesCount);
		vkIndexBuffer = createIndexBuffer();
		vkIndirectBuffer = IndirectBuffer.createIndirectBuffer(vkLogicalDevice, vkPhysicalDevice, 10, true);

		final DescriptorSetLayout descriptorSetLayout = new DescriptorSetLayout();
		final DescriptorSetLayout.BufferLayout ssboLayout = descriptorSetLayout.addBufferInfo()
				.add(2, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_VERTEX_BIT, VK_FORMAT_R32_SFLOAT, 2 * 20000); // vec2
		final DescriptorSetLayout.BufferLayout uniformBufferLayout = descriptorSetLayout.addBufferInfo()
				.add(0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT, VK_FORMAT_R32_SFLOAT, 2) // vec2
				.addSampler(1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT, textureStuff.getVkTextureImageView(), textureStuff.getVkTextureSampler()); // sampler2d
		vkSSBO = createSSBO(ssboLayout, 1);

		descriptor = new Descriptor();
		descriptor.createDescriptorSetLayout(descriptorSetLayout, vkLogicalDevice);

		vkGraphicsPipeline = new VkGraphicsPipeline();
		vkGraphicsPipeline.createGraphicsPipeline(vkLogicalDevice,
				vkExtent2D,
				vkRenderPass,
				vkSwapChainImageFormat,
				vertexBufferAttribFormat,
				descriptor.getDesciptorSetLayout(),
				"assets/shaders/indirect_ssbo/shader.vert",
				"assets/shaders/indirect_ssbo/shader.frag");

		createUniformBuffers(uniformBufferLayout.getBufferSize());

		for (int a = 0; a < framebufferList.size(); ++a) {
			final VkBuffer uniformBuffer = uniformBuffers.get(a);
			final Framebuffer framebuffer = framebufferList.get(a);
			framebuffer.addUniformBuffer(uniformBuffer);
			final DrawCommand drawCommand = new DrawCommand(
					deviceInstance,
					vkRenderPass,
					vkExtent2D,
					vkGraphicsPipeline,
					vkVertexBuffer,
					vkIndexBuffer,
					vkIndirectBuffer,
					descriptor,
					bulletsCount,
					uniformBuffer,
					framebuffer);
			drawCommand.setWindowWidth(windowWidth);
			drawCommand.setWindowHeight(windowHeight);

			this.drawCommands.add(drawCommand);
		}

		System.out.println("🔷 swapChainImageCount: " + swapChainImageCount);
		System.out.println("🔷 uniformBuffers count: " + uniformBuffers.size());
		descriptor.createDescriptorPool(descriptorSetLayout, vkLogicalDevice, swapChainImageCount);

		final List<DescriptorSetLayout.UpdateDescriptorSetInfo> updateDescriptorSetDataList = new ArrayList<>();
		for (int a = 0; a < swapChainImageCount; ++a) {
			updateDescriptorSetDataList.add(new DescriptorSetLayout.UpdateDescriptorSetInfo(a, uniformBufferLayout, uniformBuffers.get(a)));
			updateDescriptorSetDataList.add(new DescriptorSetLayout.UpdateDescriptorSetInfo(a, uniformBufferLayout, uniformBuffers.get(a)));
			updateDescriptorSetDataList.add(new DescriptorSetLayout.UpdateDescriptorSetInfo(a, ssboLayout, vkSSBO));
		}
		descriptor.createDescriptorSets(descriptorSetLayout, vkLogicalDevice, swapChainImageCount, updateDescriptorSetDataList);

		try (MemoryStack stack = MemoryStack.stackPush()) {
			final long commandPool = VkHelper.createCommandPool(vkLogicalDevice, deviceInstance.getQueue(VK_QUEUE_TRANSFER_BIT, false).getQueueInfo().queueIndex());
			final VkCommandBuffer commandBuffer = VkHelper.beginSingleTimeCommands(vkLogicalDevice, commandPool);
			bulletTest = new BulletTest(windowWidth, windowHeight, bulletsCount, vkVertexBuffer, vkIndexBuffer, vkSSBO, deviceInstance, vertexBufferAttribFormat);
			bulletTest.uploadBuffers(stack, commandBuffer);

			vkIndirectBuffer.reset();
			for (int a = 0; a < 10; ++a) {
				final int step = 2000;
				vkIndirectBuffer.addIndirectCommand(6, step, a * 6, 0, step * a);
			}
			vkIndirectBuffer.upload(stack, commandBuffer);

			VkHelper.endSingleTimeCommands(vkLogicalDevice, commandPool, commandBuffer, vkTransferQueue);
			vkDestroyCommandPool(vkLogicalDevice, commandPool, null);
		}

//		vkSSBO.deleteStagingBuffer();
	}

//	private void putVertex(ByteBuffer buffer, float x, float y, float r, float g, float b) {
//		buffer.putFloat(x).putFloat(y).putFloat(r).putFloat(g).putFloat(b);
//	}

//	private void putVertex(ByteBuffer buffer, float x, float y, float u, float v) {
//		buffer.putFloat(x).putFloat(y).putFloat(u).putFloat(v);
//	}

	private VkBuffer createIndexBuffer() {
		// create index buffer
		final VkBuffer vkBuffer = new VkBuffer(vkLogicalDevice);
		System.out.printf("Creating index buffer:\n");
		vkBuffer.createBuffer(bulletsCount * 6 * 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
		vkBuffer.allocateMemory(vkPhysicalDevice, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
		vkBuffer.makeStagingBuffer(vkPhysicalDevice, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
		return vkBuffer;
	}

	private VkBuffer createVertexBuffer(AttribFormat vertexAttribFormat, int primitivesCount) {
		final int vertexBufferSize = vertexAttribFormat.getBufferSize(primitivesCount, 4, BufferLayout.SEQUENTIAL);
		System.out.printf("Vertex buffer size: %d\n", vertexBufferSize);

		// create vertex buffer
		final VkBuffer vkBuffer = new VkBuffer(vkLogicalDevice);
		System.out.printf("Creating vertex buffer:\n");
		vkBuffer.createBuffer(vertexBufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
		vkBuffer.allocateMemory(vkPhysicalDevice, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
		vkBuffer.makeStagingBuffer(vkPhysicalDevice, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
		return vkBuffer;
	}

	private VkBuffer createSSBO(DescriptorSetLayout.BufferLayout ssboLayout, int ssboEntries) {
		final int bufferSize = ssboLayout.getBufferSize() * ssboEntries;
		System.out.printf("SSBO size: %d\n", bufferSize);

		// create shader storage buffer
		final VkBuffer vkBuffer = new VkBuffer(vkLogicalDevice);
		System.out.printf("Creating shader storage buffer:\n");
		vkBuffer.createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
		vkBuffer.allocateMemory(vkPhysicalDevice, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
		vkBuffer.makeStagingBuffer(vkPhysicalDevice, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
		return vkBuffer;
	}

	private void createUniformBuffers(int bufferSize) {
		// create uniform buffers for every framebuffer
		for (int a = 0; a < framebufferList.size(); ++a) {
			final VkBuffer uniformBuffer = new VkBuffer(vkLogicalDevice);
			System.out.printf("[%d/%d] Creating new Uniform Buffer size: %d\n", a + 1, framebufferList.size(), bufferSize);
			uniformBuffer.createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
			uniformBuffer.allocateMemory(vkPhysicalDevice, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
			uniformBuffer.makeStagingBuffer(vkPhysicalDevice, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);

			uniformBuffers.add(uniformBuffer);
		}

//		uploadData();
	}

	private void initWindow() {
		windowVK = new WindowVK();
		windowVK.create(windowWidth, windowHeight, "Vulkan2D");

		windowVK.setFramebufferSizeCallback(new GLFWFramebufferSizeCallback() {
			@Override
			public void invoke(long handle, int width, int height) {
				if (handle == windowVK.getHandle()) {
					windowWidth = width;
					windowHeight = height;

					minimized = windowWidth == 0 && windowHeight == 0;
				}
			}
		});

		windowVK.setKeyCallback(new GLFWKeyCallback() {
			private final TimeHelper inputTimer = new TimeHelper();

			@Override
			public void invoke(long hwnd, int key, int scancode, int action, int mods) {
				if (key == GLFW.GLFW_KEY_F && inputTimer.hasReachedMilli(1000)) {
					inputTimer.reset();

					windowVK.setFullscreen(!windowVK.isFullScreen(), false);
				}
				if (key == GLFW.GLFW_KEY_V && inputTimer.hasReachedMilli(250)) {
					inputTimer.reset();
					vsync = !vsync;
					shouldRecreateSwapChain = true;
				}
				if (key == GLFW.GLFW_KEY_SPACE && inputTimer.hasReachedMilli(250)) {
					inputTimer.reset();
					bulletTest.pause = !bulletTest.pause;

					if (bulletTest.pause) {
						System.out.println("Paused");
					} else {
						System.out.println("Unpaused");
					}
				}
				if (key == GLFW.GLFW_KEY_R && inputTimer.hasReachedMilli(250)) {
					inputTimer.reset();
					bulletTest.resetBullets();
				}
			}
		});
	}

	private VkSurfaceFormatKHR chooseSwapSurfaceFormat(VkSurfaceFormatKHR.Buffer availableFormats) {
		int formatIndex = 0;
		System.out.println("Choosing SwapChainSurfaceFormat...");
		for (VkSurfaceFormatKHR availableFormat : availableFormats) {
			// gives error
//			if (availableFormat.format() == VK_FORMAT_B8G8R8A8_SRGB) {
//				return availableFormat;
//			}

			try (MemoryStack stack = MemoryStack.stackPush()) {
				final VkImageFormatProperties imageFormatProperties = VkImageFormatProperties.malloc(stack);
				vkGetPhysicalDeviceImageFormatProperties(vkPhysicalDevice,
						availableFormat.format(),
						VK_IMAGE_TYPE_2D,
						VK_IMAGE_TILING_OPTIMAL,
						VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
						0,
						imageFormatProperties);

				final int maxArrayLayers = imageFormatProperties.maxArrayLayers();
				System.out.printf("[%d] VkSurfaceFormatKHR colorSpace: %d %s | format: %d %s \n",
						formatIndex,
						availableFormat.colorSpace(),
						VkHelper.translateColorSpace(availableFormat.colorSpace()),
						availableFormat.format(),
						VkHelper.translateSurfaceFormatBit(availableFormat.format()));

				if (maxArrayLayers != 0 &&
						imageFormatProperties.maxExtent().width() > 0 &&
						imageFormatProperties.maxExtent().height() > 0) {
					return availableFormat;
				}
				formatIndex++;
			}
		}

		return availableFormats.get(0);
	}

	private int chooseSwapPresentMode(int[] availablePresentModes) {
		// return VK_PRESENT_MODE_MAILBOX_KHR; // "triple buffering" vsync but with less input lag
		// return VK_PRESENT_MODE_IMMEDIATE_KHR; // no vsync
		for (int presentMode : availablePresentModes) {
			if (vsync) {
				if (presentMode == VK_PRESENT_MODE_MAILBOX_KHR ||
						presentMode == VK_PRESENT_MODE_FIFO_KHR) {
					return presentMode;
				}
			} else if (presentMode == VK_PRESENT_MODE_IMMEDIATE_KHR) {
				return presentMode;
			}
		}
		System.err.printf("Failed to find required present mode! Using the default VK_PRESENT_MODE_FIFO_KHR\n");
		return VK_PRESENT_MODE_FIFO_KHR;
	}

	private VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities) {
		if (capabilities.currentExtent().width() != Integer.MAX_VALUE) {
			final VkExtent2D extent2D = VkExtent2D.malloc(); // allocating not on stack is intentional
			extent2D.width(capabilities.currentExtent().width());
			extent2D.height(capabilities.currentExtent().height());
			return extent2D;
		} else {
			final IntBuffer fbWidth = MemoryUtil.memAllocInt(1);
			final IntBuffer fbHeight = MemoryUtil.memAllocInt(1);
			windowVK.getFramebufferSize(fbWidth, fbHeight);

			final VkExtent2D actualExtent = VkExtent2D.malloc(); // allocating not on stack is intentional
			actualExtent.width(MathUtils.clamp(fbWidth.get(0), capabilities.minImageExtent().width(), capabilities.maxImageExtent().width()));
			actualExtent.height(MathUtils.clamp(fbHeight.get(0), capabilities.minImageExtent().height(), capabilities.maxImageExtent().height()));
			memFree(fbWidth);
			memFree(fbHeight);
			return actualExtent;
		}
	}

	private void createSwapChain() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final SwapChainSupportDetails swapChainSupport = deviceInstance.querySwapChainSupport(vkPhysicalDevice, stack);

			final VkSurfaceFormatKHR surfaceFormat = chooseSwapSurfaceFormat(swapChainSupport.surfaceFormats);
			final int presentMode = chooseSwapPresentMode(swapChainSupport.presentModes);
			final VkExtent2D extent = chooseSwapExtent(swapChainSupport.capabilities);
			final int imageCount;
			final int minImageCount = swapChainSupport.capabilities.minImageCount();
			final int maxImageCount = swapChainSupport.capabilities.maxImageCount();
			imageCount = Math.max(swapChainImageCount, Math.min(minImageCount, maxImageCount));
			vkSwapChainImageFormat = surfaceFormat.format();
			if (vkExtent2D != null) {
				vkExtent2D.width(extent.width());
				vkExtent2D.height(extent.height());
				extent.free();
			} else {
				vkExtent2D = extent;
			}
			System.out.printf("Chosen format: %s\n", VkHelper.translateSurfaceFormatBit(vkSwapChainImageFormat));

			final VkSwapchainCreateInfoKHR swapchainCreateInfoKHR = VkSwapchainCreateInfoKHR.calloc(stack)
					.sType$Default()
					.surface(vkSurface)
					.minImageCount(imageCount)
					.imageFormat(vkSwapChainImageFormat)
					.imageColorSpace(surfaceFormat.colorSpace())
					.imageExtent(extent)
					.imageArrayLayers(1)
					.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
					.preTransform(swapChainSupport.capabilities.currentTransform()) // image rotation/flip
					.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) // non transparent framebuffer
					.presentMode(presentMode)
					.clipped(true)
					.oldSwapchain(NULL);

			final List<VkQueue2> queues = deviceInstance.getVkQueueDataList();
			System.out.printf("🔷 queue count: %d\n", queues.size());

			final IntBuffer queueFamilyIndices = stack.mallocInt(queues.size());
			int index = 0;
			for (VkQueue2 queue2 : queues) {
				System.out.printf("\t[swapchainCreateInfoKHR] queueFamilyIndices [%d]: %d\n", index, queue2.getQueueInfo().queueIndex());
				queueFamilyIndices.put(index, queue2.getQueueInfo().queueIndex());
				index++;
			}
			if (queues.size() > 1) {
				swapchainCreateInfoKHR.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
						.queueFamilyIndexCount(queues.size())
						.pQueueFamilyIndices(queueFamilyIndices);
				System.out.println("🔷 SwapChain ImageSharingMode: VK_SHARING_MODE_CONCURRENT");
			} else {
				swapchainCreateInfoKHR.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
						.queueFamilyIndexCount(queues.size())
						.pQueueFamilyIndices(queueFamilyIndices);
				System.out.println("🔷 SwapChain ImageSharingMode: VK_SHARING_MODE_EXCLUSIVE");
			}

			final LongBuffer swapChainRef = stack.mallocLong(1);
			if (vkCreateSwapchainKHR(vkLogicalDevice, swapchainCreateInfoKHR, null, swapChainRef) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create swap chain!");
			}
			vkSwapchainKHR = swapChainRef.get(0);

			final IntBuffer imageCountInSwapChainRef = stack.mallocInt(1);
			vkGetSwapchainImagesKHR(vkLogicalDevice, vkSwapchainKHR, imageCountInSwapChainRef, null);
			vkSwapChainImages = MemoryUtil.memAllocLong(imageCountInSwapChainRef.get(0)); // allocating not on stack is intentional
			vkGetSwapchainImagesKHR(vkLogicalDevice, vkSwapchainKHR, imageCountInSwapChainRef, vkSwapChainImages);

			System.out.printf("🔷 SwapChainImageCount: %d | framebuffers count: %d\n", imageCountInSwapChainRef.get(0), swapChainImageCount);
		}
	}

	private void createImageViews() {
		final int swapChainImagesCount = vkSwapChainImages.capacity();
		vkSwapChainImageViews = new long[swapChainImagesCount];

		for (int a = 0; a < swapChainImagesCount; ++a) {
			vkSwapChainImageViews[a] = VkHelper.createTextureImageView(vkLogicalDevice, vkSwapChainImages.get(a), vkSwapChainImageFormat);
		}
	}

	public void createRenderPass() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.calloc(1, stack)
					.format(vkSwapChainImageFormat)
					.samples(VK_SAMPLE_COUNT_1_BIT)
					.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR) // clear framebuffer before using it
					.storeOp(VK_ATTACHMENT_STORE_OP_STORE)
					.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
					.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
					.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED) // VK_IMAGE_LAYOUT_UNDEFINED dont care what image was before rendering cuz it will be cleared anyway
					.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR); // image used for displaying on screen

			final VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.calloc(1, stack)
					.attachment(0)
					.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

			final VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
					.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
					.colorAttachmentCount(1)
					.pColorAttachments(colorAttachmentRef)
					.pDepthStencilAttachment(null);

			final VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
					.srcSubpass(VK_SUBPASS_EXTERNAL)
					.dstSubpass(0)
					// wait for the swap chain to finish reading from the image before we can access it
					.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
					.srcAccessMask(0)
					// The operations that should wait on this are in the color attachment stage and involve the
					// writing of the color attachment. These settings will prevent the transition from happening until
					// it's actually necessary (and allowed): when we want to start writing colors to it.
					.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
					.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

			final VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
					.sType$Default()
					.pAttachments(colorAttachment)
					.pSubpasses(subpass)
					.pDependencies(dependency);

			final LongBuffer pRenderPass = stack.mallocLong(1);
			if (vkCreateRenderPass(vkLogicalDevice, renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create render pass!");
			}
			vkRenderPass = pRenderPass.get(0);
		}
	}

	private void createFramebuffers() {
		// we need to create framebuffer for each image view
		final int frameBufferCount = vkSwapChainImageViews.length;

		if (vkCommandPool != 0) {
			vkDestroyCommandPool(vkLogicalDevice, vkCommandPool, null);
		}
		vkCommandPool = VkHelper.createCommandPool(vkLogicalDevice, 0);

		drawCommands.clear();
		framebufferList.clear();
		for (int a = 0; a < frameBufferCount; ++a) {
			final Framebuffer framebuffer = new Framebuffer();
			if (!useDynamicRendering) {
				framebuffer.createFramebuffer(vkLogicalDevice, vkExtent2D, vkRenderPass, vkSwapChainImages.get(a), vkSwapChainImageViews[a]);
			}
			framebuffer.createSyncObjects(vkLogicalDevice);
			framebuffer.setVkCommandBuffer(VkHelper.createCommandBuffer(vkLogicalDevice, vkCommandPool));
			framebufferList.add(framebuffer);
		}
	}

//	public void putData(ByteBuffer vertexBuffer) {
//		putVertex(vertexBuffer, -0.5f, -0.5f, 0f, 0f);
//		putVertex(vertexBuffer, 0.5f, -0.5f, 1f, 0f);
//		putVertex(vertexBuffer, 0.5f, 0.5f, 1f, 1f);
//		putVertex(vertexBuffer, -0.5f, 0.5f, 0f, 1f);
//	}

//	public void uploadData() {
//		// upload data
//		final ByteBuffer vertexBuffer = vkVertexBuffer.getStagingBuffer(vkLogicalDevice);
//		vertexBuffer.clear();
//		putData(vertexBuffer);
//		vkVertexBuffer.uploadFromStagingBuffer(vkCommandPool, vkGraphicsQueue, vkLogicalDevice);
//
//		final ByteBuffer indexBuffer = vkIndexBuffer.getStagingBuffer(vkLogicalDevice);
//		indexBuffer.clear();
//		indexBuffer
//				.putShort((short) 0).putShort((short) 1).putShort((short) 2)
//				.putShort((short) 2).putShort((short) 3).putShort((short) 0);
//		vkIndexBuffer.uploadFromStagingBuffer(vkCommandPool, vkGraphicsQueue, vkLogicalDevice);
//
//		// delete staging buffers
////		vkVertexBuffer.deleteStagingBuffer(vkLogicalDevice);
////		vkIndexBuffer.deleteStagingBuffer(vkLogicalDevice);
//	}


//	/**
//	 * Less optimized
//	 * Allocates new command buffer for each upload
//	 */
//	public void uploadDataEveryFrame() {
//		// upload data
//		final ByteBuffer vertexBuffer = vkVertexBuffer.getStagingBuffer(vkLogicalDevice);
//		vertexBuffer.clear();
//		putData(vertexBuffer);
//		vkVertexBuffer.uploadFromStagingBuffer(vkCommandPool, vkGraphicsQueue, vkLogicalDevice);
//	}
//
//	/**
//	 * More optimized
//	 * Requires existing command buffer and begin command before it
//	 */
//	public void uploadDataEveryFrame(VkCommandBuffer commandBuffer) {
//		// upload data
//		final ByteBuffer vertexBuffer = vkVertexBuffer.getStagingBuffer(vkLogicalDevice);
//		vertexBuffer.clear();
//		putData(vertexBuffer);
//		vkVertexBuffer.uploadFromStagingBuffer(commandBuffer);
//	}

	private void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final Framebuffer framebuffer = framebufferList.get(imageIndex);
			final VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
					.sType$Default()
					.flags(0) // Optional
					.pInheritanceInfo(null); // Optional

			if (vkBeginCommandBuffer(commandBuffer, beginInfo) != VK_SUCCESS) {
				throw new RuntimeException("Failed to begin recording command buffer!");
			}

//			if (bulletTest.shouldUpdate()) {
//				bulletTest.onRecordCommandBuffer(framebuffer.getVkCommandBuffer());
//
//				final VkBuffer uniformBuffer = uniformBuffers.get(imageIndex);
//				final ByteBuffer buffer = uniformBuffer.getStagingBuffer(vkLogicalDevice);
//				buffer.putFloat(0, windowWidth);
//				buffer.putFloat(4, windowHeight);
//				uniformBuffer.uploadFromStagingBuffer(commandBuffer);
//			}

			final VkClearValue.Buffer clearColor = VkClearValue.calloc(1, stack);
//			// XNA background color
//			clearColor.put(0, VkHelper.vkGetClearValue(stack, 100, 150, 238, 255));
			clearColor.put(0, VkHelper.vkGetClearValue(stack, 0, 0, 0, 255));

			final VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
					.sType$Default()
					.renderPass(vkRenderPass)
					.framebuffer(framebuffer.getVkSwapChainFramebuffer())
					.clearValueCount(1)
					.pClearValues(clearColor);
			renderPassInfo.renderArea().offset().set(0, 0);
			renderPassInfo.renderArea().extent(vkExtent2D);

			vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

			// bind pipeline (shaders)
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

			// bind vertex buffer
			vkVertexBuffer.bindVertexBuffer(commandBuffer);

			vkIndexBuffer.bindIndexBuffer(commandBuffer, VK_INDEX_TYPE_UINT32);

			final LongBuffer pDescriptorSet = stack.longs(descriptor.getDescriptorSet(imageIndex));
			vkCmdBindDescriptorSets(commandBuffer,
					VK_PIPELINE_BIND_POINT_GRAPHICS,
					vkGraphicsPipeline.getVkPipelineLayout(), 0, pDescriptorSet, null);

			// actual draw command OMG
//			vkCmdDraw(commandBuffer, 3, 1, 0, 0);
			// 550 - 555 fps no pause
			// 555 fps pause

			// 559 fps no pause
			// 557 fps pause
			vkCmdDrawIndexed(commandBuffer, 6 * bulletsCount, 1, 0, 0, 0);

			vkCmdEndRenderPass(commandBuffer);

			if (vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) {
				throw new RuntimeException("Failed to record command buffer!");
			}
		}
	}

	private void recreateSwapChain(MemoryStack stack) {
		this.shouldRecreateSwapChain = false;

		final IntBuffer fbWidth = stack.mallocInt(1);
		final IntBuffer fbHeight = stack.mallocInt(1);
		windowVK.getFramebufferSize(fbWidth, fbHeight);
		if (fbWidth.get(0) == 0 ||
				fbHeight.get(0) == 0) {
			sleep(100);
			return;
		}
		System.out.printf("SwapChain creation. Framebuffer %d %d, extent %d %d\n", fbWidth.get(0), fbHeight.get(0), vkExtent2D.width(), vkExtent2D.height());

		vkDeviceWaitIdle(vkLogicalDevice);

		// cleanupSwapChain();
		if (!useDynamicRendering) {
			for (Framebuffer framebuffer : framebufferList) {
				framebuffer.onRecreateSwapChain_1(vkLogicalDevice);
			}
		}
		vkDestroySwapchainKHR(vkLogicalDevice, vkSwapchainKHR, null);
		//

		createSwapChain();
		createImageViews();

		// createFramebuffers();
		if (!useDynamicRendering) {
			for (int a = 0; a < framebufferList.size(); ++a) {
				final Framebuffer framebuffer = framebufferList.get(a);
				framebuffer.onRecreateSwapChain_2(vkLogicalDevice, vkExtent2D, vkRenderPass, vkSwapchainKHR, vkSwapChainImages.get(a), vkSwapChainImageViews[a]);
			}
		}
		//

		// remove this and enable Riva Tuner Overlay for really cool visual glitch, and also trigger this function
		for (DrawCommand drawCommand : drawCommands) {
			drawCommand.setDrawAndUploadCommandCached(false);
			drawCommand.setDrawCommandCached(false);
		}
	}

	private void cleanup() {
		textureStuff.delete(vkLogicalDevice);

		for (VkBuffer uniformBuffer : uniformBuffers) {
			uniformBuffer.destroyAndFreeMemory();
		}

		descriptor.free(vkLogicalDevice);

		if (!useDynamicRendering) {
			for (Framebuffer framebuffer : framebufferList) {
				framebuffer.destroy(vkLogicalDevice);
			}
		}
		for (DrawCommand drawCommand : drawCommands) {
			drawCommand.free();
		}
		vkDestroyCommandPool(vkLogicalDevice, vkCommandPool, null);

		vkGraphicsPipeline.destroyPipelineAndLayout(vkLogicalDevice);
		vkDestroyRenderPass(vkLogicalDevice, vkRenderPass, null);

		vkVertexBuffer.destroyAndFreeMemory();
		vkIndexBuffer.destroyAndFreeMemory();
		vkSSBO.destroyAndFreeMemory();
		vkIndirectBuffer.free();

		vkExtent2D.free();
		for (long imageView : vkSwapChainImageViews) {
			vkDestroyImageView(vkLogicalDevice, imageView, null);
		}
		memFree(vkSwapChainImages);
		memFree(pCurrentFrameIndex);
		vkDestroySwapchainKHR(vkLogicalDevice, vkSwapchainKHR, null);

		deviceInstance.destroy();

		windowVK.destroyWindow();
		windowVK.terminate();
	}

	public static void main(String[] args) {
		new VK2D();
	}

	private void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (Exception ignored) {

		}
	}
}
