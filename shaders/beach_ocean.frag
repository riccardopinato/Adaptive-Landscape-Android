#version 460 core
#include <flutter/runtime_effect.glsl>

// Beach v0.4: independent GPU aerial water. The embedded 24x42 RGB565 atlas
// provides only broad *original* photo-derived color/depth distribution.
// Glints, caustics, surf and microtexture are calculated per output fragment.
// No static coastline, sky, borrowed shader, external network or stock photo.
uniform vec2 uSize;
uniform float uPhase;
uniform float uPalette;
uniform float uNight;
uniform float uHasLand;
uniform float uQuality; // 0=balanced 1=high 2=ultra
uniform sampler2D uOccupancy;
uniform sampler2D uToneAtlas;
out vec4 fragColor;

float hash21(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float valueNoise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 w = f * f * (3.0 - 2.0 * f);
  return mix(
    mix(hash21(i), hash21(i + vec2(1.0, 0.0)), w.x),
    mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), w.x),
    w.y);
}

float fbm3(vec2 p) {
  float value = 0.0;
  float weight = 0.57;
  for (int i = 0; i < 3; i++) {
    value += weight * valueNoise(p);
    p = mat2(1.77, -0.89, 0.89, 1.77) * p + vec2(13.5, 20.7);
    weight *= 0.48;
  }
  return value;
}

float crest(float x, float width) {
  float a = x / width;
  return exp(-a * a);
}

void main() {
  vec2 coord = FlutterFragCoord().xy;
  vec2 uv = clamp(coord / max(uSize, vec2(1.0)), vec2(0.0), vec2(1.0));
  float phase = uPhase;
  float ultra = step(1.5, uQuality);
  float high = step(0.5, uQuality);
  float occupied = texture(uOccupancy, uv).r * uHasLand;

  // Invariant: no selected icons/zones means exactly zero sand and foam.
  float irregular = 0.007 * sin(uv.y * 36.0 + uv.x * 11.0) +
                    0.005 * sin(uv.y * 78.0 - uv.x * 27.0);
  float tide = 0.011 * sin(phase + uv.y * 8.4) +
               0.006 * sin(2.0 * phase - uv.y * 16.4);
  float shoreline = occupied - 0.345 + occupied * irregular + uHasLand * tide;
  float landMask = uHasLand * smoothstep(-0.004, 0.034, shoreline);
  float shallow = uHasLand * smoothstep(-0.33, -0.014, shoreline);

  // Sample the self-authored ocean tonal atlas with cover cropping on unusual
  // phone/desktop aspect ratios. The atlas is deliberately a macro reference,
  // never advertised as a 4K source photograph.
  float viewportAspect = uSize.x / max(uSize.y, 1.0);
  float atlasAspect = 24.0 / 42.0;
  vec2 photoUV = uv;
  if (viewportAspect > atlasAspect) {
    photoUV.y = 0.5 + (uv.y - 0.5) * atlasAspect / viewportAspect;
  } else {
    photoUV.x = 0.5 + (uv.x - 0.5) * viewportAspect / atlasAspect;
  }
  vec2 flow = vec2(0.027 * cos(phase), 0.020 * sin(phase));
  float currentLarge = fbm3(uv * vec2(4.1, 7.6) + flow);
  vec2 warp = vec2(
    fbm3(uv * vec2(11.0, 16.0) + flow * 0.77),
    fbm3(uv * vec2(10.0, 13.0) - flow * 0.41));
  vec2 displaced = uv + (warp - vec2(0.5)) * 0.008;
  photoUV += (warp - vec2(0.5)) * 0.0025;
  vec3 photographicTone = texture(uToneAtlas, clamp(photoUV, vec2(0.001), vec2(0.999))).rgb;
  float background = fbm3(displaced * vec2(24.0, 36.0) + flow);
  float shadows = fbm3(displaced * vec2(41.0, 65.0) - flow * 0.35);
  float small = high * fbm3(displaced * vec2(102.0, 157.0) + flow * 1.7);

  // Transparent, deep-aqua photometric appearance. Subtle bathymetric shadows,
  // no hard bands or physically impossible white stripes across the open sea.
  vec3 water = mix(
    vec3(0.024, 0.428, 0.563),
    photographicTone,
    0.88);
  water = mix(water, vec3(0.31, 0.77, 0.72), shallow * 0.62);
  water *= 0.94 + 0.105 * currentLarge;
  water += (background - 0.46) * vec3(0.021, 0.037, 0.033);
  water -= (shadows - 0.52) * vec3(0.031, 0.040, 0.035);
  water += (small - 0.48) * high * vec3(0.016, 0.027, 0.025);

  // Fine intersecting caustic fronts, non-tiled domain-warped and attenuated
  // in deep water; frequencies adapt to preview size to avoid moire/aliasing.
  float detailX = min(225.0, max(30.0, uSize.x * 0.31));
  float detailY = min(300.0, max(48.0, uSize.y * 0.24));
  vec2 rippleUV = displaced * vec2(detailX, detailY);
  rippleUV += (warp - vec2(0.48)) * 11.0;
  float r1 = sin(rippleUV.x * 0.97 + rippleUV.y * 0.34 +
    2.0 * phase + background * 5.5);
  float r2 = sin(rippleUV.y * 0.84 - rippleUV.x * 0.45 -
    phase + shadows * 7.0);
  float ridge = max(0.0, 1.0 - abs(r1 * 0.64 + r2 * 0.36));
  float caustic = pow(ridge, 9.0);
  caustic *= smoothstep(0.12, 0.90, high + shallow * 0.25);
  water += caustic * (0.016 + 0.061 * shallow) * vec3(0.67, 0.89, 0.82);

  // 4K detail pass: fine sparkling microfacets shaped by localized normals.
  // At lower quality this whole path is skipped, preserving battery/UX.
  if (ultra > 0.5) {
    vec2 fineUV = displaced * vec2(
      min(330.0, uSize.x * 0.39),
      min(455.0, uSize.y * 0.35));
    fineUV += warp * 5.0;
    float micro = valueNoise(fineUV + flow * 2.0);
    float microridge = sin(fineUV.x * 1.37 +
      fineUV.y * 0.71 + micro * 3.4 + 2.0 * phase);
    float highlight = pow(max(0.0, 0.58 * microridge +
      0.42 * r2), 14.0);
    float facet = smoothstep(0.82, 0.97, micro);
    water += vec3(0.027, 0.048, 0.049) * (highlight * 0.78 + facet * 0.24);
    water += (micro - 0.5) * vec3(0.008, 0.012, 0.010);
  }

  // Underwater sand, wet sand and soft dry cream where icons form land.
  float granular = high * valueNoise(uv * vec2(268.0, 390.0));
  vec3 wetSand = vec3(0.738, 0.727, 0.678);
  vec3 drySand = vec3(0.964, 0.931, 0.856);
  vec3 sand = mix(wetSand, drySand, smoothstep(0.008, 0.24, shoreline));
  sand += (granular - 0.5) * vec3(0.037, 0.031, 0.025);
  vec3 result = mix(water, sand, landMask);

  // Thin, broken wash: one leading and one receding foam contour.
  float jagged = (background - 0.46) * 0.015 +
                 (shadows - 0.50) * 0.006;
  float leading = crest(shoreline + jagged, 0.012);
  float receding = crest(shoreline + 0.052 +
    0.008 * sin(2.0 * phase + uv.y * 27.0 + currentLarge * 3.0), 0.011);
  float speckle = smoothstep(0.33, 0.71,
    background + (small - 0.4) * 0.11);
  float foam = uHasLand * min(0.86,
    leading * (0.55 + 0.30 * speckle) +
    receding * 0.34 * speckle);
  result = mix(result, vec3(0.969, 0.982, 0.953), foam);

  if (uPalette > 1.5) {
    result *= vec3(0.93, 1.04, 0.995);
  } else if (uPalette > 0.5) {
    result *= vec3(1.05, 0.970, 0.905);
  }
  if (uNight > 0.5) {
    result = result * vec3(0.49, 0.60, 0.71) +
             vec3(0.0, 0.008, 0.026);
  }
  float dither = (hash21(coord) - 0.5) / 350.0;
  fragColor = vec4(clamp(result + dither, vec3(0.0), vec3(1.0)), 1.0);
}
