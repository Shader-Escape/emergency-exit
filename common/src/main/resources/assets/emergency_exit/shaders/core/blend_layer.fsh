#version 330
#extension GL_ARB_separate_shader_objects : require

// Joins a channel into the accumulated layer. Per pixel the nearer one goes in front. Colours are premultiplied.
uniform sampler2D AccColor;
uniform sampler2D AccDepth;
uniform sampler2D ExitColor;
uniform sampler2D ExitDepth;
// Every declared texture must be bound, so only declare what this variant reads.
#ifdef DIM_PERCENT
uniform sampler2D MainDepth;
#endif
#if defined(HIDE_UNDER_HAND) || defined(DIM_PERCENT)
uniform sampler2D OpaqueDepth;
#endif
#ifdef HIDE_UNDER_HAND
uniform sampler2D NoHandDepth;
#endif

layout(location = 0) out vec4 fragColor;

const float TRANSLUCENT_EXISTS = 1.0005;
const float BEHIND = 0.999;

// Depth is reversed, so closeness is larger for nearer depths and 0 far away.
float closeness(float depth) { return depth; }

// HIDE_UNDER_HAND drops the pixels of the hand. DIM_PERCENT is the brightness behind translucent surfaces.
void main() {
    ivec2 pixelCoords = ivec2(gl_FragCoord.xy);
    vec4 acc = texelFetch(AccColor, pixelCoords, 0);
    float accDepth = texelFetch(AccDepth, pixelCoords, 0).r;
    vec4 color = texelFetch(ExitColor, pixelCoords, 0);
    float exitDepth = texelFetch(ExitDepth, pixelCoords, 0).r;

#ifdef HIDE_UNDER_HAND
    if (texelFetch(OpaqueDepth, pixelCoords, 0).r != texelFetch(NoHandDepth, pixelCoords, 0).r) {
        color = vec4(0.0);
    }
#endif

#ifdef DIM_PERCENT
    float mainDepth = closeness(texelFetch(MainDepth, pixelCoords, 0).r);
    float opaqueDepth = closeness(texelFetch(OpaqueDepth, pixelCoords, 0).r);
    bool translucentInFront = mainDepth > opaqueDepth * TRANSLUCENT_EXISTS;
    if (translucentInFront && closeness(exitDepth) < mainDepth * BEHIND) {
        color *= float(DIM_PERCENT) / 100.0;
    }
#endif

    // Depth only means something where something was drawn.
    if (color.a <= 0.0) {
        fragColor = acc;
        gl_FragDepth = accDepth;
        return;
    }
    if (acc.a <= 0.0) {
        fragColor = color;
        gl_FragDepth = exitDepth;
        return;
    }
    bool inFront = closeness(exitDepth) >= closeness(accDepth);
    fragColor = inFront ? color + acc * (1.0 - color.a) : acc + color * (1.0 - acc.a);
    gl_FragDepth = inFront ? exitDepth : accDepth;
}
