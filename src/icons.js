// Birdy's own icon set: small SVGs on a 24×24 grid, drawn in the UI palette
// with the plum outline (no emoji, so they look the same on every phone).
// Each icon is a list of parts: [path, fill] or [path, null, strokeColour]
// for open strokes (waves, arcs), or ['circle', cx, cy, r, fill].

const INK = '#543847';
const C = {
  gold: '#fcb800', orange: '#f26b1d', green: '#73bf2e', red: '#e8453c', blue: '#4ab8ff',
  violet: '#8f63d6', cream: '#fff6d5', white: '#ffffff', ink: INK, grey: '#c9d1d9',
  pink: '#ff8fb8', teal: '#2fa58f', sky: '#8fd3ff', yellow: '#ffe14a', coin: '#ffcf33',
};

const rr = (x, y, w, h, r) => `M${x + r} ${y}h${w - 2 * r}a${r} ${r} 0 0 1 ${r} ${r}v${h - 2 * r}a${r} ${r} 0 0 1 -${r} ${r}h-${w - 2 * r}a${r} ${r} 0 0 1 -${r} -${r}v-${h - 2 * r}a${r} ${r} 0 0 1 ${r} -${r}z`;

export const ICONS = {
  sound: [['M3.5 9h4l5-4.5v15l-5-4.5h-4z', C.gold], ['M16 9a4 4 0 0 1 0 6', null, C.white], ['M18.5 6.5a7.5 7.5 0 0 1 0 11', null, C.white]],
  mute: [['M3.5 9h4l5-4.5v15l-5-4.5h-4z', C.gold], ['M16 9.5l5 5M21 9.5l-5 5', null, C.red]],
  gift: [[rr(4, 11, 16, 9.5, 1), C.red], [rr(3, 7.5, 18, 4, 1), C.red], ['M10.8 7.5h2.4v13h-2.4z', C.gold],
    ['M12 7.5C10 3.5 5.5 3.5 6 6.5c.4 2 4 1 6 1zM12 7.5c2-4 6.5-4 6-1-.4 2-4 1-6 1z', C.gold]],
  trophy: [['M7 6H4v1.5a3.5 3.5 0 0 0 3.5 3.5M17 6h3v1.5a3.5 3.5 0 0 1-3.5 3.5', null, C.gold],
    ['M6.5 3.5h11v5.5a5.5 5.5 0 0 1-11 0z', C.gold], ['M10.8 14.5h2.4v3h-2.4z', C.gold], [rr(7.5, 17.5, 9, 3.5, 1), C.orange]],
  lock: [['M8 11V8a4 4 0 0 1 8 0v3', null, C.grey], [rr(5.5, 10.5, 13, 10, 2), C.gold], ['circle', 12, 15, 1.4, INK]],
  bird: [['M3.5 13.5c0-4.5 3.4-7.8 7.8-7.8 3.3 0 5.4 1.8 6.5 4.3l3.2-.3-2.3 3.4c-.5 4-3.6 6.9-7.8 6.9-4.4 0-7.4-2.3-7.4-6.5z', C.gold],
    ['M6.5 13.5c2.4-.2 4.6 1 5 3.3-2.6.3-4.6-1-5-3.3z', C.cream], ['circle', 14.3, 10.2, 1.3, INK], ['M18.5 10l3.5 1.3-3.6 1.2', C.orange]],
  palette: [['M12 3a9 9 0 1 0 0 18c1.6 0 2.2-1 2.2-2.1S13 17.3 13 16.3c0-1 .9-1.6 2-1.6h2.2A3.8 3.8 0 0 0 21 11c0-4.5-4-8-9-8z', C.cream],
    ['circle', 7.3, 11.5, 1.6, C.red], ['circle', 9, 7.2, 1.6, C.gold], ['circle', 13.6, 6.3, 1.6, C.green], ['circle', 17.2, 9.2, 1.6, C.blue]],
  paw: [['circle', 12, 15.5, 4, C.cream], ['circle', 6.5, 11, 2, C.cream], ['circle', 9.5, 7, 2, C.cream], ['circle', 14.5, 7, 2, C.cream], ['circle', 17.5, 11, 2, C.cream]],
  tophat: [['M7 16.5V5.5a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v11', C.ink], ['M7 12.5h10v3H7z', C.red],
    ['M2.5 17.5c0-1.2 4.3-2.3 9.5-2.3s9.5 1.1 9.5 2.3-4.3 2.3-9.5 2.3-9.5-1.1-9.5-2.3z', C.ink]],
  glasses: [['M3.5 11.5L2 8M20.5 11.5L22 8', null, C.ink], ['circle', 7, 13, 3.8, C.ink], ['circle', 17, 13, 3.8, C.ink],
    ['M10.3 12.2c1.1-.9 2.3-.9 3.4 0', null, C.ink], ['circle', 5.8, 11.8, 1, C.white], ['circle', 15.8, 11.8, 1, C.white]],
  beak: [['M3 12c3-4 9-6 18-1-9 5-15 3-18 1z', C.orange], ['M3 12c5 1 11 1 18-1', null, C.ink]],
  sparkle: [['M11 3l2.2 6.4L19.5 12l-6.3 2.6L11 21l-2.2-6.4L2.5 12l6.3-2.6z', C.yellow], ['M19 2.5l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z', C.white]],
  globe: [['circle', 12, 12, 9, C.blue],
    ['M6.2 7.5c2-.8 4.3.2 4.1 2.3-.2 2-2.8 2.2-2 4.3.8 2-.8 3.3-2 2.3C4.5 14.6 4 9.5 6.2 7.5zM13.5 4.2c2.4.2 4.8 1.7 5.9 3.9-1.2 1-3.2.1-4 2.1-.8 1.9 1.3 2.8 3.2 3 .1 2.4-2 4.6-4.4 5.4.1-2.2-2-3.2-2-5.2s1.4-2.6.2-4.2c-1.1-1.4-.9-3.6 1.1-5z', C.green]],
  pipe: [['M7.5 10h9v11h-9z', C.green], ['M9.3 10h1.6v11H9.3z', '#b2ea6c'], [rr(5, 4, 14, 6, 1), C.green], ['M7 4h1.8v6H7z', '#b2ea6c']],
  bolt: [['M13.5 2L5 13.5h6.2L9.5 22 19 9.8h-6.3z', C.gold]],
  dice: [[rr(4, 4, 16, 16, 3), C.white], ['circle', 8.5, 8.5, 1.5, INK], ['circle', 15.5, 8.5, 1.5, INK], ['circle', 12, 12, 1.5, INK], ['circle', 8.5, 15.5, 1.5, INK], ['circle', 15.5, 15.5, 1.5, INK]],
  magnet: [['M4.5 4h5v8.3a2.5 2.5 0 0 0 5 0V4h5v8.3a7.5 7.5 0 0 1-15 0z', C.red], ['M4.5 4h5v3.5h-5zM14.5 4h5v3.5h-5z', C.grey]],
  rainbow: [['M2.8 18.5a9.2 9.2 0 0 1 18.4 0', null, C.red], ['M6 18.5a6 6 0 0 1 12 0', null, C.gold], ['M9.2 18.5a2.8 2.8 0 0 1 5.6 0', null, C.blue]],
  mushroom: [['M8.8 12.5h6.4v5.5a2.5 2.5 0 0 1-2.5 2.5h-1.4a2.5 2.5 0 0 1-2.5-2.5z', C.cream],
    ['M2.5 13a9.5 8.5 0 0 1 19 0z', C.violet], ['circle', 8, 9.3, 1.5, C.white], ['circle', 14.5, 7.2, 1.3, C.white], ['circle', 17.2, 11, 1.1, C.white]],
  clover: [['circle', 12, 7.5, 3.6, C.green], ['circle', 7.5, 12, 3.6, C.green], ['circle', 16.5, 12, 3.6, C.green], ['circle', 12, 16.5, 3.6, C.green], ['M12 13l3 8', null, C.green]],
  hand: [['M10 21.5c-2.2 0-3.4-1.2-4.4-3.2l-2-4.2c-.6-1.2.7-2.3 1.8-1.6L8 14.8V5.3a1.6 1.6 0 0 1 3.2 0v5.2V9.6a1.6 1.6 0 0 1 3.2 0v1.8-.8a1.6 1.6 0 0 1 3.2 0v1.3a1.6 1.6 0 0 1 3.2 0v5.2c0 2.6-2 4.4-4.5 4.4z', C.cream]],
  check: [['M4.5 12.5l5 5 10-11', null, C.green]],
  star: [['M12 2.8l2.8 5.9 6.4.8-4.7 4.4 1.2 6.4L12 17.2l-5.7 3.1 1.2-6.4L2.8 9.5l6.4-.8z', C.gold]],
  pause: [[rr(6, 4.5, 4, 15, 1.2), C.white], [rr(14, 4.5, 4, 15, 1.2), C.white]],
  coin: [['circle', 12, 12, 8.5, C.coin], ['circle', 12, 12, 5.2, '#f5b800'], ['M10.2 9.3c.8-1.2 2.8-1.4 3.6-.2', null, C.white]],
};

// Styles for the icon set (see docs/VISUAL.md). 'sticker': colour fills with
// the plum outline. 'glyph': white shapes with outline and a drop shadow, like
// the game's lettering. 'badge': a white glyph on a coloured round badge.
export function iconSvg(name, { style = 'sticker', size = 24, badge = '#f26b1d' } = {}) {
  const parts = ICONS[name];
  const sw = 2;
  const draw = (fill, stroke, strokeW, dy = 0) => parts.map((p) => {
    const t = dy ? ` transform="translate(0 ${dy})"` : '';
    if (p[0] === 'circle') {
      const f = fill ? fill(p[4]) : p[4];
      return `<circle cx="${p[1]}" cy="${p[2]}" r="${p[3]}" fill="${f}" stroke="${stroke}" stroke-width="${strokeW}"${t}/>`;
    }
    const [d, f, line] = p;
    if (!f) {
      // Open stroke: a wider plum stroke underneath gives it its outline.
      const col = fill ? fill(line) : line;
      if (!strokeW) return `<path d="${d}" fill="none" stroke="${col}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"${t}/>`;
      return `<path d="${d}" fill="none" stroke="${stroke}" stroke-width="${2.6 + strokeW}" stroke-linecap="round" stroke-linejoin="round"${t}/>`
        + (strokeW ? `<path d="${d}" fill="none" stroke="${col}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"${t}/>` : '');
    }
    return `<path d="${d}" fill="${fill ? fill(f) : f}" stroke="${stroke}" stroke-width="${strokeW}" stroke-linejoin="round"${t}/>`;
  }).join('');
  let body;
  if (style === 'glyph') {
    const white = (c) => (c === INK ? INK : C.white);
    body = `<g opacity="1">${draw(() => INK, INK, sw, 1.4)}</g>${draw(white, INK, sw)}`;
  } else if (style === 'badge') {
    const white = (c) => (c === INK ? INK : C.white);
    body = `<circle cx="12" cy="12" r="11" fill="${badge}" stroke="${INK}" stroke-width="1.6"/>`
      + `<g transform="translate(12 12) scale(0.62) translate(-12 -12)">${draw(white, 'none', 0)}</g>`;
  } else {
    body = draw(null, INK, sw);
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="-1 -1 26 26" width="${size}" height="${size}" aria-hidden="true">${body}</svg>`;
}
