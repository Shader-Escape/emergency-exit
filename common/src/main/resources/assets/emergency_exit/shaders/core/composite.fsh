#version 330
#extension GL_ARB_separate_shader_objects : require

// Every declared texture must be bound, so only declare what this variant reads.
uniform sampler2D ExitColor;
#if defined(DIM_PERCENT) || defined(OCCLUDED_BY_WORLD)
uniform sampler2D ExitDepth;
#endif
#if defined(STRETCHED) || defined(DIM_PERCENT)
uniform sampler2D MainDepth;
#endif
#if defined(HIDE_UNDER_HAND) || defined(DIM_PERCENT) || defined(OCCLUDED_BY_WORLD)
uniform sampler2D OpaqueDepth;
#endif
#ifdef HIDE_UNDER_HAND
uniform sampler2D NoHandDepth;
#endif

layout(location = 0) out vec4 fragColor;

const float TRANSLUCENT_EXISTS = 1.0005;
const float BEHIND = 0.999;
const float OCCLUDED = 1.0005;

// Depth is standard (near = 0, far = 1). Closeness is larger for nearer depths and 0 far away.
float closeness(float depth) { return 1.0 - depth; }

float slope(sampler2D depth, ivec2 p) {
    ivec2 last = textureSize(depth, 0) - 1;
    float c = closeness(texelFetch(depth, p, 0).r);
    float l = closeness(texelFetch(depth, max(p - ivec2(1, 0), ivec2(0)), 0).r);
    float r = closeness(texelFetch(depth, min(p + ivec2(1, 0), last), 0).r);
    float d = closeness(texelFetch(depth, max(p - ivec2(0, 1), ivec2(0)), 0).r);
    float u = closeness(texelFetch(depth, min(p + ivec2(0, 1), last), 0).r);
    return max(min(abs(r - c), abs(c - l)), min(abs(u - c), abs(c - d)));
}

// HIDE_UNDER_HAND drops the pixels of the hand. DIM_PERCENT is the brightness behind translucent surfaces.
void main() {
#ifdef STRETCHED
    vec2 renderSize = vec2(textureSize(ExitColor, 0));
    vec2 outputSize = vec2(textureSize(MainDepth, 0));
    ivec2 pixelCoords = min(ivec2(gl_FragCoord.xy * renderSize / outputSize), ivec2(renderSize) - 1);
#else
    ivec2 pixelCoords = ivec2(gl_FragCoord.xy);
#endif
    vec4 color = texelFetch(ExitColor, pixelCoords, 0);
    if (color.a <= 0.0) {
        discard;
    }

#ifdef OCCLUDED_BY_WORLD
    float world = closeness(texelFetch(OpaqueDepth, pixelCoords, 0).r) - slope(OpaqueDepth, pixelCoords);
    if (world > closeness(texelFetch(ExitDepth, pixelCoords, 0).r) * OCCLUDED) {
        discard;
    }
#endif

#ifdef HIDE_UNDER_HAND
    // The depth differs before and after the hand where the hand is. The hand must stay on top.
    if (texelFetch(OpaqueDepth, pixelCoords, 0).r != texelFetch(NoHandDepth, pixelCoords, 0).r) {
        discard;
    }
#endif

#ifdef DIM_PERCENT
    float exitDepth = closeness(texelFetch(ExitDepth, pixelCoords, 0).r);
    float mainDepth = closeness(texelFetch(MainDepth, pixelCoords, 0).r);
    float opaqueDepth = closeness(texelFetch(OpaqueDepth, pixelCoords, 0).r);

    // A translucent surface makes the main depth nearer than the depth before translucents. If that surface is
    // nearer than the channel, the channel is behind it.
    bool translucentInFront = mainDepth > opaqueDepth * TRANSLUCENT_EXISTS;
    bool behindTranslucent = translucentInFront && exitDepth < mainDepth * BEHIND;

    // The colour is premultiplied, so scaling it changes both brightness and opacity.
    fragColor = behindTranslucent ? color * (float(DIM_PERCENT) / 100.0) : color;
#else
    fragColor = color;
#endif
}
