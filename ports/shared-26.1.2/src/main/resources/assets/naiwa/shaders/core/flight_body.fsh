#version 330
uniform sampler2D Sampler0;
#moj_import <minecraft:dynamictransforms.glsl>
in vec2 texCoord;
in vec3 illumination;
out vec4 fragColor;
void main() {
    vec4 color = texture(Sampler0,texCoord);
    if(color.a<0.1) discard;
    fragColor = vec4(color.rgb*illumination,color.a)*ColorModulator;
}
