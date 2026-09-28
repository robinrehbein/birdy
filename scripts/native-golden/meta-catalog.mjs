// Golden fixture: a full, canonical dump of src/catalog.js's static tables
// (SKINS, PATTERNS, HATS, EYES, BEAKS, TRAILS, WORLDS, PIPES, UPGRADES),
// imported directly (the module has no side effects). Colours are recorded
// both as the original 0xRRGGBB JS numbers and as "#rrggbb" strings for a
// Kotlin Int/Color-agnostic port. This file IS the canonical source for the
// shop item tables — a Kotlin engineer can transcribe from this JSON without
// re-reading catalog.js.
// Run: node scripts/native-golden/meta-catalog.mjs
// Writes: docs/native/golden/meta-catalog.json
import { writeFileSync } from 'node:fs';
import { SKINS, PATTERNS, HATS, EYES, BEAKS, TRAILS, WORLDS, PIPES, CATALOG, KINDS, UPGRADES, UPGRADE_MAX } from '../../src/catalog.js';

const hex = (n) => (n === undefined ? undefined : '#' + n.toString(16).padStart(6, '0'));

function colorFields(obj, fields) {
  const o = { ...obj };
  for (const f of fields) if (o[f] !== undefined) o[f + 'Hex'] = hex(o[f]);
  return o;
}

const SKIN_COLOR_FIELDS = ['body', 'belly', 'wing', 'cover', 'tail', 'beak', 'beakLow'];
const TRAIL_COLOR_FIELDS = ['colors'];
const WORLD_COLOR_FIELDS = ['top', 'horizon', 'hemiSky', 'hemiGround', 'sun', 'tint', 'clouds', 'grass', 'track'];
const PIPE_COLOR_FIELDS = ['pipe', 'light', 'dark'];

const out = { source: 'src/catalog.js' };

out.skins = SKINS.map((s) => {
  const o = colorFields(s, SKIN_COLOR_FIELDS);
  return o;
});
out.patterns = PATTERNS;
out.hats = HATS;
out.eyes = EYES;
out.beaks = BEAKS;
out.trails = TRAILS.map((t) => ({ ...t, colorsHex: t.colors.map(hex) }));
out.worlds = WORLDS.map((w) => {
  const o = colorFields(w, WORLD_COLOR_FIELDS);
  if (w.road) o.roadHex = w.road.map(hex);
  if (w.pipes) o.pipesHex = { pipe: hex(w.pipes.pipe), light: hex(w.pipes.light), dark: hex(w.pipes.dark) };
  return o;
});
out.pipes = PIPES.map((p) => colorFields(p, PIPE_COLOR_FIELDS));
out.upgrades = UPGRADES;
out.upgradeMax = UPGRADE_MAX;
out.kinds = KINDS;

// Sanity: every kind's first item MUST be price 0 (the always-owned free
// starter item; see progress.js load() `const free = CATALOG[kind][0].id`).
out.freeFirstItemPerKind = Object.fromEntries(KINDS.map((k) => [k, { id: CATALOG[k][0].id, price: CATALOG[k][0].price }]));
out.allFirstItemsAreFree = KINDS.every((k) => CATALOG[k][0].price === 0);

// Item counts per kind (for UI grid layout / test coverage).
out.countsPerKind = Object.fromEntries(KINDS.map((k) => [k, CATALOG[k].length]));

// Rare/animated skins: id -> fx name + swatch CSS gradient (swatch is only
// used by the JS shop UI's CSS background; a Kotlin/Compose UI recreates the
// same visual with its own gradient brush, see skinfx.js spec for the fx
// itself which drives the in-scene 3D material, not this shop tile).
out.rareSkins = SKINS.filter((s) => s.rare).map((s) => ({ id: s.id, fx: s.fx, price: s.price, swatchCss: s.swatch }));

writeFileSync(new URL('../../docs/native/golden/meta-catalog.json', import.meta.url), JSON.stringify(out, null, 2));
console.log('wrote meta-catalog.json');
