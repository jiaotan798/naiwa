#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    // 保留接近透明的柔边，不沿用实体着色器的 0.1 截断阈值。
    fragColor = texture(Sampler0, texCoord) * vertexColor * ColorModulator;
    if (fragColor.a < 0.001) discard;
}
