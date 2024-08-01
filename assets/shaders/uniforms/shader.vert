#version 450

layout (location = 0) in vec2 aPosition;
layout (location = 1) in vec3 aColor;

layout (location = 0) out vec3 vColor;

layout (binding = 0) uniform UniformBufferObject {
	mat4 model;
	mat4 view;
	mat4 proj;
} ubo;

void main() {
	gl_Position = ubo.proj * ubo.view * ubo.model * vec4(aPosition, 0.0, 1.0);
	vColor = aColor;
}