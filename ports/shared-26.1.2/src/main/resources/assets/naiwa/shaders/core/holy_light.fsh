#version 330

uniform sampler2D Sampler0;
#moj_import <minecraft:dynamictransforms.glsl>
in vec2 texCoord;
in vec4 vertexColor;
in vec3 beamOffset;
flat in int mode;
out vec4 fragColor;

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    float alpha = vertexColor.a;
    vec3 color = vec3(1.0, 0.81, 0.4);
    if (mode == 0) {
        float r = length(centered);
        color = mix(vec3(1.0, 0.83, 0.5), vec3(1.0, 0.99, 0.92), exp(-r*r*5.0));
        alpha *= min(1.0, exp(-r*r*3.0)*1.2) * (1.0-smoothstep(0.82,1.0,r));
    } else if (mode == 1) {
        float edge = pow(max(0.0,1.0-centered.x*centered.x),3.0);
        float height = 1.0-texCoord.y;
        // 留出躯干周围的空间，斜光片经过角色时不切出明暗接缝。
        alpha *= edge * mix(0.9,1.0,texture(Sampler0,texCoord).a)
            * smoothstep(1.8,3.2,length(beamOffset))
            * smoothstep(0.0,0.018,height) * (1.0-smoothstep(0.82,1.0,height));
    } else if (mode == 2) {
        float r = length(centered);
        color = vec3(1.0,0.83,0.4);
        alpha *= exp(-r*r*4.0)*0.25*(1.0-smoothstep(0.82,1.0,r));
    } else if (mode == 3) {
        color = vec3(1.0,0.94,0.72);
        alpha *= exp(-dot(centered,centered)*4.0)*(1.0-smoothstep(0.6,1.0,length(centered)));
    } else {
        color = mix(vec3(0.42,0.48,0.57),vec3(1.0,0.83,0.48),vertexColor.g);
        alpha *= pow(max(0.0,1.0-abs(centered.x)),1.5) * sin(texCoord.y*3.14159265359);
    }
    fragColor = vec4(color,alpha) * ColorModulator;
    if (fragColor.a < 0.001) discard;
}
