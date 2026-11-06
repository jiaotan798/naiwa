#version 150
uniform sampler2D DiffuseSampler;
uniform vec2 BlurDirection;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 color = texture(DiffuseSampler,texCoord).rgb*0.227027;
    color += (texture(DiffuseSampler,texCoord+BlurDirection).rgb+texture(DiffuseSampler,texCoord-BlurDirection).rgb)*0.1945946;
    color += (texture(DiffuseSampler,texCoord+BlurDirection*2.0).rgb+texture(DiffuseSampler,texCoord-BlurDirection*2.0).rgb)*0.1216216;
    color += (texture(DiffuseSampler,texCoord+BlurDirection*3.0).rgb+texture(DiffuseSampler,texCoord-BlurDirection*3.0).rgb)*0.054054;
    color += (texture(DiffuseSampler,texCoord+BlurDirection*4.0).rgb+texture(DiffuseSampler,texCoord-BlurDirection*4.0).rgb)*0.016216;
    fragColor = vec4(color,1.0);
}
