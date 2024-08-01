#version 450

layout (location = 0) in vec3 vColor;

layout (location = 0) out vec4 outColor;

//layout (binding = 1) uniform UniformBufferObject_Frag {
//	vec4 color;
//} ubo_frag;

void main() {
	//	outColor = vec4(vColor, 1.0) * ubo_frag.color;
	outColor = vec4(vColor, 1.0);
}