#version 330

in vec3 Position;
in vec2 UV0;
in vec4 Color;
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
out vec2 texCoord;
out vec4 vertexColor;
out vec3 beamOffset;
flat out int mode;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
    beamOffset = Position - vec3(0.0, 1.15, 0.0);
    mode = int(floor(Color.r * 255.0 + 0.5));
}
