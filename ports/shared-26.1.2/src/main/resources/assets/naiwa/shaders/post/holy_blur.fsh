#version 330
uniform sampler2D InSampler;
layout(std140) uniform HolyBlurConfig { vec2 BlurDirection; };
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 color = texture(InSampler,texCoord).rgb*0.227027;
    color += (texture(InSampler,texCoord+BlurDirection).rgb+texture(InSampler,texCoord-BlurDirection).rgb)*0.1945946;
    color += (texture(InSampler,texCoord+BlurDirection*2.0).rgb+texture(InSampler,texCoord-BlurDirection*2.0).rgb)*0.1216216;
    color += (texture(InSampler,texCoord+BlurDirection*3.0).rgb+texture(InSampler,texCoord-BlurDirection*3.0).rgb)*0.054054;
    color += (texture(InSampler,texCoord+BlurDirection*4.0).rgb+texture(InSampler,texCoord-BlurDirection*4.0).rgb)*0.016216;
    fragColor = vec4(color,1.0);
}
