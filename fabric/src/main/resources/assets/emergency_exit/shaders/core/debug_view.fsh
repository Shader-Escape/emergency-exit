#version 330
#extension GL_ARB_separate_shader_objects : require

// MODE is the ordinal of DebugView.Mode.
uniform sampler2D ExitColor;
uniform sampler2D ExitDepth;
uniform sampler2D MainDepth;
uniform sampler2D OpaqueDepth;

layout(location = 0) out vec4 fragColor;

// Depth is standard (near = 0, far = 1). Closeness is larger for nearer depths and 0 far away.
float closeness(float depth) { return 1.0 - depth; }

// Shows closeness as grey. Far away, where the buffer was cleared, is dark blue.
vec3 depthColor(float depth) {
    depth = closeness(depth);
    if (depth <= 0.0) {
        return vec3(0.0, 0.0, 0.15);
    }
    return vec3(pow(clamp(depth, 0.0, 1.0), 0.25));
}

void main() {
#ifdef STRETCHED
    // The channel has the scene size and the output may be larger, so scale the coordinates.
    vec2 renderSize = vec2(textureSize(ExitColor, 0));
    vec2 outputSize = vec2(textureSize(MainDepth, 0));
    ivec2 pixelCoords = min(ivec2(gl_FragCoord.xy * renderSize / outputSize), ivec2(renderSize) - 1);
#else
    ivec2 pixelCoords = ivec2(gl_FragCoord.xy);
#endif

#if MODE == 1
    vec4 color = texelFetch(ExitColor, pixelCoords, 0);
    fragColor = vec4(color.rgb + vec3(0.15) * (1.0 - color.a), 1.0);
#elif MODE == 2
    fragColor = vec4(depthColor(texelFetch(ExitDepth, pixelCoords, 0).r), 1.0);
#elif MODE == 3
    fragColor = vec4(depthColor(texelFetch(OpaqueDepth, pixelCoords, 0).r), 1.0);
#elif MODE == 4
    fragColor = vec4(depthColor(texelFetch(MainDepth, pixelCoords, 0).r), 1.0);
#else
    float mainDepth = texelFetch(MainDepth, pixelCoords, 0).r;
    float opaqueDepth = texelFetch(OpaqueDepth, pixelCoords, 0).r;
    bool translucentInFront = closeness(mainDepth) > closeness(opaqueDepth) * 1.0005;
    fragColor = vec4(translucentInFront ? depthColor(mainDepth) : vec3(0.0, 0.0, 0.15), 1.0);
#endif
}
