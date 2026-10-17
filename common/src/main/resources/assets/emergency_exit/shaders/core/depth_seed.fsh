#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D OpaqueDepth;
uniform sampler2D NoHandDepth;

const float PUSH = 0.9995;

// Depth is reversed, so closeness is larger for nearer depths and 0 far away.
float closeness(float depth) { return depth; }
float depthOf(float closeness) { return closeness; }

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
    gl_FragDepth = depthOf(closeness(opaque) * PUSH);
#else
    discard;
#endif
}
