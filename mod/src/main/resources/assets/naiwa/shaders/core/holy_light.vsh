#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 texCoord;
out vec4 vertexColor;
uniform vec3 BodyCenter;
out vec3 beamOffset;
flat out int mode;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
    beamOffset = Position - BodyCenter;
    mode = int(floor(Color.r * 255.0 + 0.5));
}
