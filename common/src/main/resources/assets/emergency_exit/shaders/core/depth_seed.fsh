#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D OpaqueDepth;
uniform sampler2D NoHandDepth;

const float PUSH = 0.9995;

// Depth is reversed, so closeness is larger for nearer depths and 0 far away.
float closeness(float depth) { return depth; }
float depthOf(float closeness) { return closeness; }

float slope(sampler2D depth, ivec2 p) {
    ivec2 last = textureSize(depth, 0) - 1;
    float c = closeness(texelFetch(depth, p, 0).r);
    float l = closeness(texelFetch(depth, max(p - ivec2(1, 0), ivec2(0)), 0).r);
    float r = closeness(texelFetch(depth, min(p + ivec2(1, 0), last), 0).r);
    float d = closeness(texelFetch(depth, max(p - ivec2(0, 1), ivec2(0)), 0).r);
    float u = closeness(texelFetch(depth, min(p + ivec2(0, 1), last), 0).r);
    return max(min(abs(r - c), abs(c - l)), min(abs(u - c), abs(c - d)));
}

// HIDE_UNDER_HAND keeps the hand in front. OCCLUDED_BY_WORLD lets the world hide the channel.
void main() {
    ivec2 pixelCoords = ivec2(gl_FragCoord.xy);
    float opaque = texelFetch(OpaqueDepth, pixelCoords, 0).r;

#ifdef HIDE_UNDER_HAND
    // The shader pack draws the hand with its own depth range, so its depth cannot be compared. Set the nearest
    // depth wherever the hand is, so nothing is drawn over it.
    if (opaque != texelFetch(NoHandDepth, pixelCoords, 0).r) {
        gl_FragDepth = depthOf(1.0);
        return;
    }
#endif

#ifdef OCCLUDED_BY_WORLD
    gl_FragDepth = depthOf(max(closeness(opaque) * PUSH - slope(OpaqueDepth, pixelCoords), 0.0));
#else
    discard;
#endif
}
