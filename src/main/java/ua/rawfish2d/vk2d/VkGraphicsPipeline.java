package ua.rawfish2d.vk2d;

import lombok.Getter;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import ua.rawfish2d.vk2d.attrib.AttribFormat;
import ua.rawfish2d.vk2d.vkutils.VkHelper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class VkGraphicsPipeline {
	@Getter
	private long vkPipelineLayout;
	private long vkGraphicsPipeline;

	public void createGraphicsPipeline(VkDevice vkLogicalDevice, VkExtent2D vkExtent2D, long vkRenderPass, int vkSwapChainImageFormat, AttribFormat attribFormat, long descriptorSetLayout, String vertShader, String fragShader) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final long vertShaderModule = createShaderModule(vkLogicalDevice, vertShader, VK_SHADER_STAGE_VERTEX_BIT, stack);
			final long fragShaderModule = createShaderModule(vkLogicalDevice, fragShader, VK_SHADER_STAGE_FRAGMENT_BIT, stack);

			final ByteBuffer pName = stack.UTF8("main");
			final VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);

			shaderStages.put(0, VkPipelineShaderStageCreateInfo.calloc(stack)
					.sType$Default()
					.stage(VK_SHADER_STAGE_VERTEX_BIT)
					.module(vertShaderModule)
					.pName(pName)
					.pSpecializationInfo(null));

			shaderStages.put(1, VkPipelineShaderStageCreateInfo.calloc(stack)
					.sType$Default()
					.stage(VK_SHADER_STAGE_FRAGMENT_BIT)
					.module(fragShaderModule)
					.pName(pName)
					.pSpecializationInfo(null));

			final IntBuffer dynamicStates = stack.mallocInt(2);
			dynamicStates.put(0, VK_DYNAMIC_STATE_VIEWPORT);
			dynamicStates.put(1, VK_DYNAMIC_STATE_SCISSOR);
			final VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
					.sType$Default()
					.pDynamicStates(dynamicStates);

			// vertex format similar to setting up VAO
			final VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkHelper.makeVertexAttribDescription(attribFormat, stack);

			final VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
					.sType$Default()
					.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
					.primitiveRestartEnable(false);

			final VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
			viewport.x(0.0f).y(0.0f);
			viewport.width(vkExtent2D.width()).height(vkExtent2D.height());
			viewport.minDepth(0f).maxDepth(1f);

			final VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
			scissor.offset().set(0, 0);
			scissor.extent(vkExtent2D);

			final VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
					.sType$Default()
					.pViewports(viewport)
					.viewportCount(1)
					.pScissors(scissor)
					.scissorCount(1);

			final VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
					.sType$Default()
					.depthClampEnable(false)
					.polygonMode(VK_POLYGON_MODE_FILL)
					.lineWidth(1.0f)
					.cullMode(VK_CULL_MODE_FRONT_BIT)
					.frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
					.depthBiasEnable(false)
					.depthBiasConstantFactor(0f)
					.depthBiasClamp(0f)
					.depthBiasSlopeFactor(0f);

			final VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
					.sType$Default()
					.sampleShadingEnable(false)
					.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
					.minSampleShading(1.0f) // Optional
					.pSampleMask(null) // Optional
					.alphaToCoverageEnable(false) // Optional
					.alphaToOneEnable(false); // Optional

			final VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
					.sType$Default()
					.depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
					.depthTestEnable(false)
					.depthWriteEnable(false)
					.minDepthBounds(0f)
					.maxDepthBounds(1f)
					.depthBoundsTestEnable(false)
					.stencilTestEnable(false);

			// per framebuffer blending
			final VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
					.colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
					// no blending
					.blendEnable(false)
					.srcColorBlendFactor(VK_BLEND_FACTOR_ONE) // Optional
					.dstColorBlendFactor(VK_BLEND_FACTOR_ZERO) // Optional
					.colorBlendOp(VK_BLEND_OP_ADD) // Optional
					.srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE) // Optional
					.dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO) // Optional
					.alphaBlendOp(VK_BLEND_OP_ADD); // Optional
			// alpha blending
			// glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
//					.blendEnable(true)
//					.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA) // Optional
//					.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA) // Optional
//					.colorBlendOp(VK_BLEND_OP_ADD) // Optional
//					.srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE) // Optional
//					.dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO) // Optional
//					.alphaBlendOp(VK_BLEND_OP_ADD); // Optional
			// blending
			// glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
//					.blendEnable(true)
//					.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA) // Optional
//					.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA) // Optional
//					.colorBlendOp(VK_BLEND_OP_ADD) // Optional
//					.srcAlphaBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA) // Optional
//					.dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA) // Optional
//					.alphaBlendOp(VK_BLEND_OP_ADD); // Optional

			// global blending
			final VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
					.sType$Default()
					.logicOpEnable(false)
					.logicOp(VK_LOGIC_OP_COPY) // Optional
					.attachmentCount(1)
					.pAttachments(colorBlendAttachment);
			colorBlending.blendConstants().put(0, 0f); // Optional
			colorBlending.blendConstants().put(1, 0f); // Optional
			colorBlending.blendConstants().put(2, 0f); // Optional
			colorBlending.blendConstants().put(3, 0f); // Optional

			final VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
					.sType$Default()
					.setLayoutCount(1)
					.pSetLayouts(stack.longs(descriptorSetLayout))
					.pPushConstantRanges(null); // Optional

			final LongBuffer pPipelineLayout = stack.mallocLong(1);
			if (vkCreatePipelineLayout(vkLogicalDevice, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create pipeline layout!");
			}
			vkPipelineLayout = pPipelineLayout.get(0);

			// finally
			final VkGraphicsPipelineCreateInfo.Buffer pipelineInfos = VkGraphicsPipelineCreateInfo.calloc(1, stack)
					.sType$Default()
					.stageCount(2)
					.pStages(shaderStages)
					.pVertexInputState(vertexInputInfo)
					.pInputAssemblyState(inputAssembly)
					.pViewportState(viewportState)
					.pRasterizationState(rasterizer)
					.pMultisampleState(multisampling)
					.pDepthStencilState(depthStencil) // optional
					.pColorBlendState(colorBlending)
					.pDynamicState(dynamicState)
					.layout(vkPipelineLayout)
					.subpass(0)
					.basePipelineHandle(VK_NULL_HANDLE) // optional
					.basePipelineIndex(-1); // optional

			if (VK2D.useDynamicRendering) {
				final VkPipelineRenderingCreateInfoKHR vkPipelineRenderingCreateInfoKHR = VkPipelineRenderingCreateInfoKHR.calloc(stack)
						.sType$Default()
						.colorAttachmentCount(1)
						.pColorAttachmentFormats(stack.ints(vkSwapChainImageFormat));

				pipelineInfos.renderPass(VK_NULL_HANDLE);
				pipelineInfos.pNext(vkPipelineRenderingCreateInfoKHR);
			} else {
				pipelineInfos.renderPass(vkRenderPass);
			}

			final LongBuffer pGraphicsPipeline = stack.mallocLong(1);
			final int result = vkCreateGraphicsPipelines(vkLogicalDevice, VK_NULL_HANDLE, pipelineInfos, null, pGraphicsPipeline);
			if (result != VK_SUCCESS) {
				throw new RuntimeException("Failed to create graphics pipeline! " + VkHelper.translateVulkanResult(result));
			}
			vkGraphicsPipeline = pGraphicsPipeline.get(0);

			vkDestroyShaderModule(vkLogicalDevice, fragShaderModule, null);
			vkDestroyShaderModule(vkLogicalDevice, vertShaderModule, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private long createShaderModule(VkDevice vkLogicalDevice, String shaderPath, int stage, MemoryStack stack) throws IOException {
		final ByteBuffer shaderCode = VkHelper.glslToSpirv(shaderPath, stage);
		final VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
				.sType$Default()
				.pCode(shaderCode);

		final LongBuffer shaderModule = stack.mallocLong(1);
		if (vkCreateShaderModule(vkLogicalDevice, createInfo, null, shaderModule) != VK_SUCCESS) {
			throw new RuntimeException("Failed to create shader module! Shader path: " + shaderPath);
		}
		return shaderModule.get(0);
	}

	public void destroyPipelineAndLayout(VkDevice vkLogicalDevice) {
		vkDestroyPipeline(vkLogicalDevice, vkGraphicsPipeline, null);
		vkDestroyPipelineLayout(vkLogicalDevice, vkPipelineLayout, null);
	}

	public void bindPipeline(VkCommandBuffer commandBuffer) {
		vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, vkGraphicsPipeline);
	}
}
