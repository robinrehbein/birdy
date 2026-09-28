// Golden fixture: full dump of src/icons.js's ICONS vector data + BADGE
// colours, plus rendered SVG strings for every icon in every style ('badge',
// 'glyph', 'sticker') at a fixed size, produced by the REAL iconSvg()/icon()/
// rich() functions (module has no side effects, imported directly).
// Run: node scripts/native-golden/meta-icons.mjs
// Writes: docs/native/golden/meta-icons.json
import { writeFileSync } from 'node:fs';
import { ICONS, BADGE, icon, iconSvg, rich, setRich } from '../../src/icons.js';

const out = { source: 'src/icons.js', viewBox: '-1 -1 26 26 (25x25 units, 1px bleed on a 24x24 grid)' };

// Raw vector part lists, exactly as declared (path-fill pairs / stroke
// triples / circle tuples). Kept as JS-shaped arrays for a Kotlin port that
// wants to re-walk the same draw() logic; also flattened into a friendlier
// object form below.
out.iconNames = Object.keys(ICONS);
out.badgeColors = BADGE;
out.rawParts = ICONS;

function describePart(p) {
  if (p[0] === 'circle') return { kind: 'circle', cx: p[1], cy: p[2], r: p[3], fill: p[4] };
  const [d, fill, stroke] = p;
  return fill ? { kind: 'filledPath', d, fill } : { kind: 'openStroke', d, strokeColor: stroke };
}
out.parsedParts = Object.fromEntries(Object.entries(ICONS).map(([name, parts]) => [name, parts.map(describePart)]));

out.palette = {
  ink: '#543847',
  land: '#6cbb35',
  named: {
    gold: '#fcb800', orange: '#f26b1d', green: '#73bf2e', red: '#e8453c', blue: '#4ab8ff',
    violet: '#8f63d6', cream: '#fff6d5', white: '#ffffff', grey: '#c9d1d9',
    pink: '#ff8fb8', teal: '#2fa58f', sky: '#8fd3ff', yellow: '#ffe14a', coin: '#ffcf33',
  },
};

// Render every icon's SVG in every style, size 22 (the default `icon()` UI
// size) and 24 (grid-native), to freeze exact markup for the port's own
// vector-drawing code to visually diff against.
out.renderedSvg = {};
for (const name of out.iconNames) {
  out.renderedSvg[name] = {
    badge22: icon(name, 22),
    badge24: iconSvg(name, { style: 'badge', size: 24, badge: BADGE[name] }),
    glyph24: iconSvg(name, { style: 'glyph', size: 24 }),
    sticker24: iconSvg(name, { style: 'sticker', size: 24 }),
  };
}

// rich()/setRich(): [name] tag substitution into inline badge spans.
out.richExamples = {
  formula: "str.replace(/\\[(\\w+)\\]/g, (m,n) => ICONS[n] ? `<span class=\"ic\">${icon(n,'1.35em')}</span>` : m)  — unknown [name] is left as literal text",
  example: rich('[gift] Daily gift · +20'),
  exampleUnknownTag: rich('[doesnotexist] stays literal'),
  escapeModeExample: rich('<b>bold</b> [star]', true),
};

// Style semantics (for the port's renderer, see docs comment icons.js:93-95):
out.styleSemantics = {
  sticker: 'colour fills as declared, ink-coloured (#543847) outline stroke width 2 on every path/circle',
  glyph: 'white fills (INK stays INK) with an ink outline + a 1.4-unit-down ink drop-shadow duplicate of the whole icon drawn first',
  badge: 'a filled ink-outlined circle (r=11, fill=badge colour) behind the icon; icon itself drawn scaled 0.7 and recentred, with LAND colour (#6cbb35, only used by the globe icon) mapped to the badge colour instead of white so the "continents" read against any badge tint',
};

writeFileSync(new URL('../../docs/native/golden/meta-icons.json', import.meta.url), JSON.stringify(out, null, 2));
console.log('wrote meta-icons.json');
