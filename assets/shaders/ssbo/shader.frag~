#version 450

layout (binding = 1) uniform sampler2D u_texture;

layout (location = 0) out vec4 outColor;

layout (location = 0) in FragData {
	vec2 texCoord;
	vec4 color;
} inData;

void main() {
	vec4 color = texture(u_texture, inData.texCoord);
	if (color.a < 0.001) {
		//		outColor = inData.color;
		discard;
	}
	else {
		outColor = color;
	}
	//		outColor = vec4(1.0, 0.0, 1.0, 1.0);
	//	outColor = inData.color;
}