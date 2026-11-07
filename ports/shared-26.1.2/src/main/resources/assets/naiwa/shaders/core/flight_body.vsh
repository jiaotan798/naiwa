#version 330
in vec3 Position;
in vec4 Color;
in vec2 UV0;
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
out vec2 texCoord;
out vec3 illumination;
void main() {
    vec4 view = ModelViewMat * vec4(Position,1.0);
    // 光照在 CPU 按参考公式计算；三倍范围保留高于一的轮廓高光，随后参与暖色泛光。
    illumination = Color.rgb * 3.0;
    texCoord = UV0;
    gl_Position = ProjMat * view;
}
