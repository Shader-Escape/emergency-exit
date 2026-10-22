#version 330
#extension GL_ARB_separate_shader_objects : require

// The depth before and after the render scale probe was drawn through the shader pack. Red where it changed.
uniform sampler2D Before;
uniform sampler2D After;

layout(location = 0) out vec4 fragColor;

void main() {
    ivec2 pixelCoords = ivec2(gl_FragCoord.xy);
    bool changed = texelFetch(After, pixelCoords, 0).r != texelFetch(Before, pixelCoords, 0).r;
    fragColor = vec4(changed ? 1.0 : 0.0, 0.0, 0.0, 1.0);
}
