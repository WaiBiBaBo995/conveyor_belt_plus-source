#version 150
uniform sampler2D CargoMask;
uniform sampler2D MaskDepth;
uniform sampler2D SceneDepth;
uniform vec2 TexelSize;
in vec2 uv;
out vec4 fragColor;
float sampleEdge(vec2 offset, float scene) {
    vec2 p = uv + offset * TexelSize;
    return texture(CargoMask, p).a * step(texture(MaskDepth, p).r, scene + 0.000002);
}
void main() {
    if (texture(CargoMask, uv).a > 0.5) discard;
    float scene = texture(SceneDepth, uv).r;
    float edge = 0.0;
    for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++)
        edge = max(edge, sampleEdge(vec2(x, y), scene));
    float glow = max(max(sampleEdge(vec2(-2,0),scene), sampleEdge(vec2(2,0),scene)),
                     max(sampleEdge(vec2(0,-2),scene), sampleEdge(vec2(0,2),scene)));
    float alpha = max(edge, glow * 0.3);
    if (alpha <= 0.0) discard;
    fragColor = vec4(1.0, 1.0, 1.0, alpha);
}
