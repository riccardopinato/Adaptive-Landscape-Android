#version 460 core
#include <flutter/runtime_effect.glsl>

// Independent procedural aerial seascape. UV is normalized; water is shaded
// per framebuffer fragment. No photographs, GPL shader or remote assets.
uniform vec2 uSize;
uniform float uPhase;
uniform float uPalette;
uniform float uNight;
uniform float uHasLand;
uniform sampler2D uOccupancy;
out vec4 fragColor;

float hash2(vec2 p) {
  p = fract(p * vec2(127.1, 311.7));
  p += dot(p, p + 34.91);
  return fract(p.x * p.y);
}

float noise2(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 w = f * f * (3.0 - 2.0 * f);
  float a = hash2(i);
  float b = hash2(i + vec2(1.0, 0.0));
  float c = hash2(i + vec2(0.0, 1.0));
  float d = hash2(i + vec2(1.0, 1.0));
  return mix(mix(a, b, w.x), mix(c, d, w.x), w.y);
}

float fbm(vec2 p) {
  float v = 0.0;
  float w = 0.55;
  for (int i = 0; i < 4; i++) {
    v += w * noise2(p);
    p = mat2(1.68, -1.02, 1.02, 1.68) * p + vec2(16.7, 22.2);
    w *= 0.47;
  }
  return v;
}

float bell(float x, float radius) {
  return exp(-pow(x / radius, 2.0));
}

void main() {
  vec2 pix = FlutterFragCoord().xy;
  vec2 uv = clamp(pix / max(uSize, vec2(1.0)), vec2(0.0), vec2(1.0));
  float t = uPhase; // Periodic: every animation uses integer harmonics.

  float occupied = texture(uOccupancy, uv).r * uHasLand;
  float rough = 0.008 * sin(uv.y * 39.0 + uv.x * 9.0) +
                0.004 * sin(uv.x * 54.0 - uv.y * 92.0);
  float tide = 0.012 * sin(t + uv.y * 8.2) +
               0.005 * sin(2.0 * t + uv.y * 17.0);
  float shore = occupied - 0.34 + occupied * rough + uHasLand * tide;

  // Ocean-only invariant: when occupancy is empty, the water remains across
  // every pixel; neither tide, shallow gradients nor foam may create land.
  float land = uHasLand * smoothstep(-0.005, 0.035, shore);
  float shoal = uHasLand * smoothstep(-0.31, -0.012, shore);

  // Slowly drifting, multi-scale surface distortion suggests currents and
  // reflected daylight without geometric pixels or repeated stripe textures.
  vec2 flow = vec2(0.032 * cos(t), 0.024 * sin(t));
  float large = fbm(uv * vec2(4.2, 7.4) + flow);
  vec2 warp = vec2(
    fbm(uv * vec2(11.0, 15.0) + flow * 0.65),
    fbm(uv * vec2(10.0, 13.0) - flow * 0.4));
  vec2 current = uv + (warp - vec2(0.5)) * 0.009;
  float middle = fbm(current * vec2(22.0, 32.0) + flow);
  float fine = fbm(current * vec2(84.0, 128.0) - flow * 2.0);

  vec3 seaDeep = vec3(0.019, 0.486, 0.623);
  vec3 seaMedium = vec3(0.050, 0.633, 0.707);
  vec3 seaShallow = vec3(0.348, 0.787, 0.716);
  vec3 water = mix(seaDeep, seaMedium, 0.13 + 0.42 * large);
  water = mix(water, seaShallow, shoal * 0.86);
  water += (middle - 0.5) * vec3(0.032, 0.052, 0.052);
  water += (fine - 0.5) * vec3(0.013, 0.022, 0.019);

  // Tiny, fragmented highlights: localized in water and most visible in
  // transparent shallows, not rectangular strips or stylized ripples.
  float sparkle = pow(max(0.0, 0.55 * sin(current.x * 138.0 +
                                 current.y * 42.0 + t + middle * 5.0) +
                                 0.45 * sin(current.y * 157.0 -
                                 current.x * 41.0 - 2.0 * t + fine * 6.0)), 5.0);
  float density = 0.30 + 0.70 * shoal;
  water += sparkle * density * vec3(0.055, 0.086, 0.082);

  // Soft dry cream sand, slightly darker wet shoreline, fine sand grain.
  float sandNoise = fbm(uv * vec2(220.0, 330.0));
  vec3 wetSand = vec3(0.731, 0.723, 0.684);
  vec3 drySand = vec3(0.963, 0.941, 0.885);
  vec3 sand = mix(wetSand, drySand, smoothstep(0.00, 0.24, shore));
  sand += (sandNoise - 0.5) * vec3(0.025, 0.019, 0.014);
  vec3 color = mix(water, sand, land);

  // Foam follows ONLY the shore. Organic broken details use a displaced
  // boundary and a second fading ripple behind the advancing wash.
  float foamVariation = (middle - 0.5) * 0.010 +
                        (fine - 0.5) * 0.004;
  float edge = bell(shore + foamVariation, 0.011);
  float trailing = bell(shore + 0.055 +
    0.009 * sin(2.0 * t + uv.y * 26.0 + large * 3.0), 0.010);
  float broken = smoothstep(0.37, 0.59, middle + (fine - 0.5) * 0.3);
  float foam = uHasLand * min(0.82, edge * (0.62 + 0.20 * broken) +
                                  trailing * 0.27 * broken);
  color = mix(color, vec3(0.962, 0.973, 0.939), foam);

  if (uPalette > 1.5) {
    color *= vec3(0.93, 1.03, 0.98);
  } else if (uPalette > 0.5) {
    color *= vec3(1.07, 0.95, 0.89);
  }
  if (uNight > 0.5) {
    color = color * vec3(0.49, 0.58, 0.70) + vec3(0.0, 0.010, 0.026);
  }
  float dither = (hash2(pix) - 0.5) / 340.0;
  fragColor = vec4(clamp(color + dither, vec3(0.0), vec3(1.0)), 1.0);
}
