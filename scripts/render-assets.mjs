// Renders launcher icons, splash screens, the store icon and the feature
// graphics (DE/EN) from the game's own bird model (scripts/assets/).
//
//   npx vite --port 5173 &   node scripts/render-assets.mjs [url=http://localhost:5173/]
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
const URL = process.argv[2] || 'http://localhost:5173/';
const RES = 'android/app/src/main/res';
const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage();
page.on('pageerror', (e) => console.error(e.message));
await page.goto(`${URL}scripts/assets/render.html`);
await page.waitForFunction(() => window.artReady, null, { timeout: 60000 });

async function art(file, kind, w, h = w, opts = {}, mask = null) {
  const url = await page.evaluate(([k, ww, hh, o, m]) => window.renderArt(k, ww, hh, o).then((u) => {
    if (!m) return u;
    // Legacy launcher icons: rounded square or circle with transparent corners.
    return new Promise((res) => {
      const img = new Image();
      img.onload = () => {
        const c = document.createElement('canvas');
        c.width = ww;
        c.height = hh;
        const g = c.getContext('2d');
        g.beginPath();
        if (m === 'round') g.arc(ww / 2, hh / 2, ww / 2, 0, Math.PI * 2);
        else g.roundRect(0, 0, ww, hh, ww * 0.18);
        g.clip();
        g.drawImage(img, 0, 0);
        res(c.toDataURL('image/png'));
      };
      img.src = u;
    });
  }), [kind, w, h, opts, mask]);
  fs.writeFileSync(file, Buffer.from(url.split(',')[1], 'base64'));
  console.log(file);
}

const DENSITY = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };
for (const [d, k] of Object.entries(DENSITY)) {
  await art(`${RES}/mipmap-${d}/ic_launcher.png`, 'icon', 48 * k, 48 * k, {}, 'square');
  await art(`${RES}/mipmap-${d}/ic_launcher_round.png`, 'icon', 48 * k, 48 * k, {}, 'round');
  await art(`${RES}/mipmap-${d}/ic_launcher_foreground.png`, 'icon-fg', 108 * k);
  await art(`${RES}/mipmap-${d}/ic_launcher_background.png`, 'icon-bg', 108 * k);
}
const SPLASH = { mdpi: [320, 480], hdpi: [480, 800], xhdpi: [720, 1280], xxhdpi: [960, 1600], xxxhdpi: [1280, 1920] };
for (const [d, [w, h]] of Object.entries(SPLASH)) {
  await art(`${RES}/drawable-port-${d}/splash.png`, 'splash', w, h);
  await art(`${RES}/drawable-land-${d}/splash.png`, 'splash', h, w);
}
await art(`${RES}/drawable/splash.png`, 'splash', 480, 320);
await art('docs/store/icon-512.png', 'icon', 512);
await art('docs/store/feature-birdy-1024x500.png', 'feature', 1024, 500, { tagline: 'Tippen. Ausweichen. Durchfliegen.' });
await art('docs/store/feature-birdy-1024x500-en.png', 'feature', 1024, 500, { tagline: 'Tap. Dodge. Fly through.' });
await browser.close();
