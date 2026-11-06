#version 150
uniform sampler2D DiffuseSampler;
uniform sampler2D SceneSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 scene = texture(SceneSampler,texCoord);
    float highlight = smoothstep(0.55,0.95,max(scene.r,max(scene.g,scene.b)));
    vec3 night = scene.rgb*mix(vec3(0.25,0.28,0.40),vec3(1.0),highlight);
    fragColor = vec4(night+texture(DiffuseSampler,texCoord).rgb*0.55,scene.a);
}
