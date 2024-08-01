#version 450

layout (location = 0) in vec2 aPos;
layout (location = 1) in vec2 aTexCoord;

layout (location = 0) out FragData {
	vec2 texCoord;
} outData;

layout (binding = 0) uniform UniformBufferObject {
	vec2 resolution;
} inData;
//const vec2 resolution = vec2(1024, 768);

void main() {
	float x = (aPos.x) / (inData.resolution.x / 2.0) - 1.0;
	float y = (aPos.y) / (inData.resolution.y / 2.0) - 1.0;
	//	float x = (aPos.x) / (resolution.x / 2.0) - 1.0;
	//	float y = (aPos.y) / (resolution.y / 2.0) - 1.0;
	gl_Position = vec4(x, -y, 0.0, 1.0);
	outData.texCoord = aTexCoord;
}