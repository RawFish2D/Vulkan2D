package ua.rawfish2d.vk2d.attrib;

import lombok.Getter;
import lombok.Setter;
import ua.rawfish2d.vk2d.vkutils.VkHelper;

@Getter
public class AttribInfo {
	private final int attribBinding;
	private final int attribLocation;
	private final int elementByteSize;
	private final int format;
	@Setter
	private int offset = 0;
	@Setter
	private int stride = 0;
	private final int divisor;

	public AttribInfo(int attribBinding, int attribLocation, int format, int divisor) {
		this.attribBinding = attribBinding;
		this.attribLocation = attribLocation;
		this.format = format;
		this.elementByteSize = VkHelper.vkFormatToByteCount(format);
		this.divisor = divisor;
	}

	public int getSize() {
		return elementByteSize;
	}

	public boolean isInstanced() {
		return divisor != 0;
	}
}
