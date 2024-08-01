package ua.rawfish2d.vk2d.enums;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_SAMPLER_ADDRESS_MODE_MIRROR_CLAMP_TO_EDGE;

public enum TextureWrap {
	REPEAT,
	CLAMP_TO_BORDER,
	CLAMP_TO_EDGE,
	MIRROR_REPEAT,
	MIRROR_CLAMP_TO_EDGE;

	public int get() {
		return switch (this) {
			case REPEAT -> VK_SAMPLER_ADDRESS_MODE_REPEAT;
			case CLAMP_TO_BORDER -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER;
			case CLAMP_TO_EDGE -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
			case MIRROR_REPEAT -> VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
			case MIRROR_CLAMP_TO_EDGE -> VK_SAMPLER_ADDRESS_MODE_MIRROR_CLAMP_TO_EDGE;
		};
	}
}