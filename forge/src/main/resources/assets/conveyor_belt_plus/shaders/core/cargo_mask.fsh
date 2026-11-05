#version 150
uniform sampler2D Sampler0;
uniform int Skip;
in vec2 uv;
out vec4 fragColor;
void main() { if (Skip != 0 || texture(Sampler0, uv).a < 0.08) discard; fragColor = vec4(1.0); }
