#version 330
#extension GL_ARB_separate_shader_objects : require

// Writes the depth of the translucent drawings. The depth test keeps the nearer of it and the channel's own depth.
uniform sampler2D TranslucentDepth;

void main() {
    gl_FragDepth = texelFetch(TranslucentDepth, ivec2(gl_FragCoord.xy), 0).r;
}
