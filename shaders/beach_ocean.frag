#version 460 core
#include <flutter/runtime_effect.glsl>

uniform vec2 uSize;
uniform float uPhase;
uniform float uPalette; // 0=original, 1=sunset, 2=mint
uniform float uNight;
uniform float uHasLand;
uniform sampler2D uOccupancy;
out vec4 fragColor;

float hash21(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}
float noise2(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), u.x),
             mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), u.x), u.y);
}
float fbm(vec2 p) {
  float value = 0.0;
  float amplitude = 0.5;
  for (int i = 0; i < 4; i++) {
    value += noise2(p) * amplitude;
    p = p * 2.03 + vec2(12.7, 9.3);
    amplitude *= 0.5;
  }
  return value;
}
void main() {
  vec2 uv = clamp(FlutterFragCoord().xy / max(uSize, vec2(1.0)), vec2(0.0), vec2(1.0));
  float phase = uPhase;

  // Pure ocean when all icon, folder, widget and dock markers are absent.
  // Shore depends ONLY on locally calibrated occupancy; no permanent beach.
  float occupancy = texture(uOccupancy, uv).r * uHasLand;
  float rough = 0.010 * sin(uv.y * 38.0 + 0.8) +
                0.006 * sin(uv.y * 83.0 + uv.x * 31.0);
  float tide = 0.012 * sin(phase + uv.y * 8.2) +
               0.005 * sin(2.0 * phase + uv.y * 17.0);
  float shore = occupancy - 0.32 + occupancy * rough + uHasLand * tide;

  // Pixel-resolution water optical detail, not a coarse interpolated mesh.
  vec2 drift = vec2(0.038 * cos(phase + uv.y * 3.0),
                    0.026 * sin(phase + uv.x * 2.0));
  float n = fbm(uv * vec2(7.7, 10.0) + drift);
  float causticA = sin(uv.x * 128.0 + uv.y * 41.0 + phase * 2.0 + n * 5.0);
  float causticB = sin(uv.x * 77.0 - uv.y * 103.0 - phase * 2.0 + n * 4.0);
  float glint = pow(max(0.0, 0.48 * causticA + 0.52 * causticB), 3.0);
  float fine = fbm(uv * vec2(120.0, 220.0) - drift * 1.8);
  float shallow = smoothstep(-0.36, -0.02, shore);
  vec3 deepSea = vec3(0.032, 0.365, 0.515);
  vec3 turquoise = vec3(0.055, 0.720, 0.760);
  vec3 water = mix(deepSea, turquoise, 0.35 + 0.65 * shallow);
  water += (n - 0.5) * vec3(0.037, 0.064, 0.067);
  water += fine * vec3(0.012, 0.019, 0.018);
  water += glint * vec3(0.080, 0.136, 0.124);

  // Wet rose/beige sand, soft shore transparency, dry cream sand.
  float sandGrain = fbm(uv * vec2(240.0, 340.0));
  vec3 drySand = vec3(0.961, 0.919, 0.819);
  vec3 wetSand = vec3(0.755, 0.714, 0.669);
  vec3 sand = mix(wetSand, drySand, smoothstep(0.02, 0.30, shore));
  sand += (sandGrain - 0.5) * vec3(0.047, 0.035, 0.030);

  float sandMask = smoothstep(-0.008, 0.010, shore) * uHasLand;
  vec3 color = mix(water, sand, sandMask);

  // Thin, moving foam exactly at emerged land boundary.
  float foamCenter = exp(-pow(shore / 0.012, 2.0));
  float foamBack = exp(-pow((shore + 0.052 +
                           0.007 * sin(2.0 * phase + uv.y * 31.0)) / 0.009, 2.0));
  float foamNoise = smoothstep(0.34, 0.65, fbm(uv * vec2(190.0, 300.0) + drift));
  float foam = uHasLand * clamp(foamCenter * 0.72 + foamBack * foamNoise * 0.30, 0.0, 0.88);
  color = mix(color, vec3(0.967, 0.986, 0.945), foam);

  if (uPalette > 1.5) {
    color *= vec3(0.93, 1.035, 0.99);
  } else if (uPalette > 0.5) {
    color *= vec3(1.075, 0.97, 0.89);
  }
  if (uNight > 0.5) color = color * vec3(0.47, 0.58, 0.72) + vec3(0.0, 0.008, 0.029);
  // Per-fragment subtle dither reduces color banding at high DPI.
  float dither = (hash21(FlutterFragCoord().xy) - 0.5) / 320.0;
  fragColor = vec4(clamp(color + dither, vec3(0.0), vec3(1.0)), 1.0);
}
