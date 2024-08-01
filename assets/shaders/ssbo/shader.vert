#version 460

//#extension GL_ARB_shader_draw_parameters : enable
//#extension SPV_KHR_shader_draw_parameters : enable
//#extension VK_KHR_shader_draw_parameters : enable

layout (location = 0) in vec2 aPos;
layout (location = 1) in vec2 aTexCoord;

layout (location = 0) out FragData {
	vec2 texCoord;
	vec4 color;
} outData;

layout (binding = 0) uniform UniformBufferObject {
	vec2 resolution;
} inData;

layout (std140, set = 0, binding = 2) readonly buffer SSBO {
	vec4 data;
} ssbo;

void main() {
	float x = (aPos.x) / (inData.resolution.x / 2.0) - 1.0;
	float y = (aPos.y) / (inData.resolution.y / 2.0) - 1.0;
	//	float x = (aPos.x) / (resolution.x / 2.0) - 1.0;
	//	float y = (aPos.y) / (resolution.y / 2.0) - 1.0;
	gl_Position = vec4(x, -y, 0.0, 1.0);
	outData.texCoord = aTexCoord;
	//	if (gl_DrawID == 0) {
	//		outData.color = vec4(1.0, 1.0, 1.0, 1.0);
	//	}
	//	else {
	//		outData.color = vec4(1.0, 0.0, 1.0, 1.0);
	//	}
	//	outData.color = ssbo.data;
}