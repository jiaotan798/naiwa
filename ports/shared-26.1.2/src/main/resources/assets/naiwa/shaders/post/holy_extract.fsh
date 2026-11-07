#version 330
uniform sampler2D InSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 color = texture(InSampler,texCoord).rgb;
    float warm = 1.0-smoothstep(0.02,0.18,color.b-color.r);
    float bright = smoothstep(0.55,0.95,min(color.r,color.g));
    fragColor = vec4(color*warm*bright,1.0);
}
