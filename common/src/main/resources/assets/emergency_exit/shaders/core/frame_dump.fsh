#version 330
#extension GL_ARB_separate_shader_objects : require

// Pictures of EXIT's buffers for FrameDump. MODE 0: depth A as grey. 1: premultiplied colour A over a checkerboard.
// 2: depth A, orange where depth B differs (the hand). 3: depth A, orange where A is nearer than B (translucent in front).
// 4: colour A as it is, without alpha.
uniform sampler2D A;
#if MODE == 2 || MODE == 3
uniform sampler2D B;
#endif

layout(location = 0) out vec4 fragColor;

const float TRANSLUCENT_EXISTS = 1.0005;
const vec3 MARK = vec3(1.0, 0.55, 0.1);

// Depth is reversed, so closeness is larger for nearer depths and 0 far away. Far is dark blue, like the debug view.
vec3 depthColor(float depth) {
    if (depth <= 0.0) {
        return vec3(0.0, 0.0, 0.15);
    }
    return vec3(pow(clamp(depth, 0.0, 1.0), 0.25));
}

void main() {
    ivec2 pixelCoords = ivec2(gl_FragCoord.xy);
#if MODE == 4
    fragColor = vec4(texelFetch(A, pixelCoords, 0).rgb, 1.0);
#elif MODE == 1
    vec4 color = texelFetch(A, pixelCoords, 0);
    ivec2 cell = pixelCoords / 8;
    vec3 checker = (cell.x + cell.y) % 2 == 0 ? vec3(0.22) : vec3(0.3);
    fragColor = vec4(color.rgb + checker * (1.0 - color.a), 1.0);
#else
    float a = texelFetch(A, pixelCoords, 0).r;
    vec3 base = depthColor(a);
#if MODE == 2
    bool marked = a != texelFetch(B, pixelCoords, 0).r;
#elif MODE == 3
    bool marked = a > texelFetch(B, pixelCoords, 0).r * TRANSLUCENT_EXISTS;
#else
    bool marked = false;
#endif
    fragColor = vec4(marked ? mix(base, MARK, 0.85) : base, 1.0);
#endif
}
