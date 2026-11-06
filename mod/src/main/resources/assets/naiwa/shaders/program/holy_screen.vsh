#version 150
in vec4 Position;
uniform vec2 OutSize;
out vec2 texCoord;
void main() {
    // 每遍使用输出目标的真实尺寸，半分辨率目标不借用全尺寸正交矩阵。
    texCoord = Position.xy / OutSize;
    gl_Position = vec4(texCoord * 2.0 - 1.0, 0.0, 1.0);
}
