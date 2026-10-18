#version 330
#extension GL_ARB_separate_shader_objects : require

// The scene depth as the shader pack placed it. Per axis the pack's pixel is SCALE * pixel + OFFSET, in fixed point.
uniform sampler2D Source;

void main() {
    vec2 scale = vec2(float(SCALE_X), float(SCALE_Y)) / float(FIXED);
    vec2 offset = vec2(float(OFFSET_X), float(OFFSET_Y)) / float(FIXED);
    ivec2 sourceCoords = clamp(ivec2(floor(gl_FragCoord.xy * scale + offset)), ivec2(0), textureSize(Source, 0) - 1);
    gl_FragDepth = texelFetch(Source, sourceCoords, 0).r;
}
