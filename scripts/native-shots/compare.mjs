// Side-by-side sheets of the native shots (native/build/shots) and the JS
// reference (native/build/shots-js): one PNG per scene name in
// native/build/shots-compare/, plus an index sheet with all pairs.
//   node scripts/native-shots/compare.mjs
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
let chromium;
try {
  ({ chromium } = require('playwright'));
} catch {
  ({ chromium } = require(`${execSync('npm root -g').toString().trim()}/playwright`));
}
process.env.PLAYWRIGHT_BROWSERS_PATH ||= '/opt/pw-browsers';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../..');
const NATIVE = path.join(ROOT, 'native/build/shots');
const JS = path.join(ROOT, 'native/build/shots-js');
const OUT = path.join(ROOT, 'native/build/shots-compare');
fs.mkdirSync(OUT, { recursive: true });

const names = fs.readdirSync(NATIVE).filter((f) => /^\d\d-.*\.png$/.test(f)).sort();
const img = (dir, f) => (fs.existsSync(path.join(dir, f)) ? `data:image/png;base64,${fs.readFileSync(path.join(dir, f)).toString('base64')}` : null);
const cell = (src, label) => `<figure><figcaption>${label}</figcaption>${src ? `<img src="${src}">` : '<div class="none">no shot</div>'}</figure>`;
const css = `body{margin:0;background:#2b3a42;font:20px system-ui;color:#fff;display:flex;gap:16px;padding:16px;flex-wrap:wrap}
figure{margin:0}figcaption{padding:4px 0}img,.none{width:405px;height:900px;display:block;object-fit:fill;background:#111}
.none{display:flex;align-items:center;justify-content:center;color:#888}h2{width:100%;margin:0}`;

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 900, height: 1000 } });
for (const f of names) {
  await page.setContent(`<style>${css}</style>${cell(img(JS, f), `JS · ${f}`)}${cell(img(NATIVE, f), `native · ${f}`)}`);
  await page.screenshot({ path: path.join(OUT, f), fullPage: true });
  console.log('wrote', f);
}
await browser.close();
