// Golden fixture for bird.js's wingColor() rule (bird.js lines 131-139):
// decides whether a skin's own wing colour is used, or whether the wings are
// repainted in a body-tinted tone (to avoid "stick" wings on pale-wing
// skins). Computed here for every skin in catalog.js SKINS so the port can
// check its own HSL-lightness math against this table.
// Run: node scripts/native-golden/bird-fx-wing-color-rule.mjs
import { writeFileSync } from 'node:fs';

// Minimal copy of the SKINS table (id, body, belly, wing, cover, tail) from
// src/catalog.js — only the fields wingColor() reads.
const SKINS = [
  { id: 'sunny', body: 0xf7d23e, belly: 0xfff3c4, wing: 0xfff6d5, cover: 0xf6e3a1, tail: 0xf2c230 },
  { id: 'sky', body: 0x4aa8f0, belly: 0xe8f6ff, wing: 0xdff1ff, cover: 0x9fd2fa, tail: 0x2f86d0 },
  { id: 'cardinal', body: 0xe8453c, belly: 0xffd7c9, wing: 0xffe3dc, cover: 0xf28b82, tail: 0xc4302b },
  { id: 'robin', body: 0x9a6b4a, belly: 0xff8a3d, wing: 0xe9d6c4, cover: 0xb88a66, tail: 0x7d5238 },
  { id: 'mint', body: 0x5fd39a, belly: 0xeafff3, wing: 0xe3fff0, cover: 0xa6ecc8, tail: 0x3bb37b },
  { id: 'coral', body: 0xff7f6b, belly: 0xfff0e6, wing: 0xffe5dc, cover: 0xffb3a3, tail: 0xe8604e },
  { id: 'flamingo', body: 0xff8fb8, belly: 0xffe6f0, wing: 0xfff0f6, cover: 0xffc2d8, tail: 0xf2649a },
  { id: 'parrot', body: 0x3cc45a, belly: 0xffe14a, wing: 0xff5a4a, cover: 0x4ab8ff, tail: 0x2f86d0 },
  { id: 'penguin', body: 0x3a4250, belly: 0xffffff, wing: 0x505a6c, cover: 0x2e3440, tail: 0x2e3440 },
  { id: 'night', body: 0x5b4b8a, belly: 0xd9d0f5, wing: 0xc7bdf0, cover: 0x8f80c9, tail: 0x44376e },
  { id: 'snowy', body: 0xf4f7fb, belly: 0xffffff, wing: 0xdde6f0, cover: 0xc9d6e3, tail: 0xb8c6d6 },
  { id: 'peacock', body: 0x1f8fb0, belly: 0x7be0c8, wing: 0x3ccfa0, cover: 0x2a6fd0, tail: 0x1ea06a },
  { id: 'gold', body: 0xffc629, belly: 0xfff1b0, wing: 0xffe57a, cover: 0xffd23d, tail: 0xe0a100 },
  { id: 'toadstool', body: 0xe0302a, belly: 0xfff3e0, wing: 0xe0302a, cover: 0xc4241f, tail: 0xc4241f },
  { id: 'basketball', body: 0xf26b1d, belly: 0xffa860, wing: 0xf26b1d, cover: 0xd9530f, tail: 0xd9530f },
  { id: 'football', body: 0xf4f4f4, belly: 0xffffff, wing: 0xe6e6e6, cover: 0x2a2a2a, tail: 0x2a2a2a },
  { id: 'water', body: 0x1e7fd6, belly: 0xbfe9ff, wing: 0x1e7fd6, cover: 0x3aa0f0, tail: 0x1466b0 },
  { id: 'lava', body: 0x3a2420, belly: 0x5a3028, wing: 0x3a2420, cover: 0x2a1a18, tail: 0x2a1a18 },
  { id: 'diamond', body: 0x9fe0ff, belly: 0xe6fbff, wing: 0x9fe0ff, cover: 0x7fd0f5, tail: 0x7fd0f5 },
  { id: 'galaxy', body: 0x2a1a60, belly: 0x6a3a9a, wing: 0x2a1a60, cover: 0x4a2a8a, tail: 0x4a2a8a },
];

// three.js Color.getHSL() uses linear-light HSL when colorSpace is
// LinearSRGBColorSpace, but bird.js calls tmp.getHSL(hsl, THREE.SRGBColorSpace)
// explicitly, which asks three.js to report HSL of the value AS STORED
// (already sRGB, i.e. the raw 0xRRGGBB hex interpreted directly as sRGB
// components) rather than converting through linear space first. That is
// equivalent to plain hex -> sRGB -> HSL, i.e. the standard CSS/Photoshop
// lightness of the hex colour. This fixture uses that standard formula.
function hexToRgb(hex) {
  return { r: ((hex >> 16) & 255) / 255, g: ((hex >> 8) & 255) / 255, b: (hex & 255) / 255 };
}
function rgbToHsl({ r, g, b }) {
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  const l = (max + min) / 2;
  if (max === min) return { h: 0, s: 0, l };
  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  let h;
  if (max === r) h = (g - b) / d + (g < b ? 6 : 0);
  else if (max === g) h = (b - r) / d + 2;
  else h = (r - g) / d + 4;
  h /= 6;
  return { h, s, l };
}
function lerpColor(aHex, bHex, t) {
  const a = hexToRgb(aHex), b = hexToRgb(bHex);
  const r = a.r + (b.r - a.r) * t, g = a.g + (b.g - a.g) * t, bl = a.b + (b.b - a.b) * t;
  return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(bl * 255);
}

function wingColor(skin) {
  const { l } = rgbToHsl(hexToRgb(skin.wing));
  if (l < 0.8) return { branch: 'ownWing', wing: skin.wing, cover: skin.cover, lightness: l };
  const w = lerpColor(skin.body, skin.belly, 0.12);
  return { branch: 'bodyTinted', wing: w, cover: skin.tail, lightness: l };
}

const rows = SKINS.map((skin) => ({ id: skin.id, ...wingColor(skin) }));

const out = {
  source: 'src/bird.js lines 131-139',
  rule: 'if HSL-lightness(skin.wing) < 0.8: keep skin.wing/skin.cover as-is. else: wing = lerp(skin.body, skin.belly, 0.12) in RGB, cover = skin.tail.',
  purpose: 'Prevents pale-wing skins (e.g. sunny\'s cream wing, penguin/snowy near-white) from reading as invisible "stick" wings against the sky; repaints them in a body-family tone instead.',
  threshold: 0.8,
  lerpFactor: 0.12,
  perSkinResults: rows,
};
writeFileSync(new URL('../../docs/native/golden/bird-fx-wing-color-rule.json', import.meta.url), JSON.stringify(out, null, 2) + '\n');
console.log(`wrote ${rows.length} skin rows`);
