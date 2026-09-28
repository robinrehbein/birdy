// Golden fixture for engine/texture RasterCanvas: world.js drawGround (512x128) for two road
// palettes and makeGrassTexture (4x128), rendered by Chromium's 2D canvas via Playwright.
// Stores selected full rows of getImageData (RGBA, base64) plus whole-image channel sums.
//   node scripts/native-golden/engine-texture.mjs   (needs playwright, e.g. npm root -g)
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import fs from 'node:fs';

const require = createRequire(import.meta.url);
let chromium;
try {
  ({ chromium } = require('playwright'));
} catch {
  ({ chromium } = require(`${execSync('npm root -g').toString().trim()}/playwright`));
}

// world.js drawGround / makeGrassTexture, verbatim (css() inlined).
const PAGE_SCRIPT = `
const css = (hex) => '#' + hex.toString(16).padStart(6, '0');
function drawGround(g, [road, stripe, edge]) {
  g.clearRect(0, 0, 512, 128);
  const x0 = 150, x1 = 362;
  g.fillStyle = css(road);
  g.fillRect(x0, 0, x1 - x0, 128);
  g.strokeStyle = css(stripe);
  g.lineWidth = 14;
  for (let i = -128; i < 256; i += 32) {
    g.beginPath();
    g.moveTo(x0, i);
    g.lineTo(x1, i + 128);
    g.stroke();
  }
  g.fillStyle = 'rgba(255, 252, 235, 0.75)';
  for (const x of [226.5, 285.5]) g.fillRect(x - 2.5, 8, 5, 48);
  g.fillStyle = css(edge);
  g.fillRect(x0 - 10, 0, 10, 128);
  g.fillRect(x1, 0, 10, 128);
  g.fillStyle = '#543847';
  g.fillRect(x0 - 14, 0, 4, 128);
  g.fillRect(x1 + 10, 0, 4, 128);
}
function grass(g) {
  g.fillStyle = '#ffffff';
  g.fillRect(0, 0, 4, 128);
  g.fillStyle = '#e0e6d8';
  for (let y = 0; y < 128; y += 32) g.fillRect(0, y, 4, 16);
}
function shapes(g) {
  // Extra primitives the engine supports: gradient, globalAlpha, arc fill.
  const gr = g.createLinearGradient(0, 0, 64, 32);
  gr.addColorStop(0, '#ff0000');
  gr.addColorStop(0.5, 'rgba(0, 255, 0, 0.5)');
  gr.addColorStop(1, '#0000ff');
  g.fillStyle = gr;
  g.fillRect(0, 0, 64, 32);
  g.globalAlpha = 0.6;
  g.fillStyle = '#ffffff';
  g.beginPath();
  g.arc(40, 40, 17.5, 0, Math.PI * 2);
  g.fill();
  g.globalAlpha = 1;
}
window.render = (kind, palette) => {
  const c = document.createElement('canvas');
  if (kind === 'ground') { c.width = 512; c.height = 128; drawGround(c.getContext('2d'), palette); }
  else if (kind === 'grass') { c.width = 4; c.height = 128; grass(c.getContext('2d')); }
  else { c.width = 64; c.height = 64; shapes(c.getContext('2d')); }
  const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
  let s = '';
  for (let i = 0; i < d.length; i++) s += String.fromCharCode(d[i]);
  return { width: c.width, height: c.height, data: btoa(s) };
};
`;

const browser = await chromium.launch();
const page = await browser.newPage();
await page.setContent('<html><body></body></html>');
await page.addScriptTag({ content: PAGE_SCRIPT });

async function shot(kind, palette, rows) {
  const r = await page.evaluate(([k, p]) => window.render(k, p), [kind, palette]);
  const bytes = Buffer.from(r.data, 'base64');
  const rowLen = r.width * 4;
  const sums = [0, 0, 0, 0];
  for (let i = 0; i < bytes.length; i++) sums[i % 4] += bytes[i];
  const pickRows = rows ?? [...Array(r.height).keys()];
  return {
    kind, palette, width: r.width, height: r.height, channelSums: sums,
    rows: Object.fromEntries(pickRows.map((y) => [y, bytes.subarray(y * rowLen, (y + 1) * rowLen).toString('base64')])),
  };
}

const ROWS = [0, 3, 7, 8, 20, 31, 32, 55, 56, 64, 90, 100, 127];
const out = {
  description: 'Chromium 2D canvas output of world.js drawGround/makeGrassTexture (+ a gradient/arc/globalAlpha sample) for engine/texture RasterCanvas. rows: base64 RGBA of full rows (non-premultiplied, getImageData).',
  images: [
    await shot('ground', [0xded895, 0xd2c26a, 0x9ce659], ROWS),
    await shot('ground', [0xeef4fb, 0xd6e4f2, 0xbfe3ff], ROWS),
    await shot('grass', null, null),
    await shot('shapes', null, null),
  ],
};
await browser.close();
fs.writeFileSync(new URL('../../docs/native/golden/engine-texture.json', import.meta.url), JSON.stringify(out) + '\n');
console.log('wrote engine-texture.json');
