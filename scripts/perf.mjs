// Render-budget probe: starts a run and samples draw calls, triangles and
// GPU memory objects while playing. (Headless GPU is software-rendered, so
// the fps number here is NOT representative of a phone.)
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';

const require = createRequire(import.meta.url);
let chromium;
try {
  ({ chromium } = require('playwright'));
} catch {
  ({ chromium } = require(`${execSync('npm root -g').toString().trim()}/playwright`));
}
const url = process.argv[2] || 'http://localhost:4173/';
const browser = await chromium.launch({
  args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
const t0 = Date.now();
await page.goto(url);
await page.waitForFunction(() => window.__birdy?.renderer?.info.render.frame > 2);
const firstFrame = Date.now() - t0;
await page.click('#play-btn');
await page.screenshot({ path: process.env.SHOT || '/dev/null' }).catch(() => {});
const samples = [];
for (let i = 0; i < 12; i++) {
  await page.mouse.click(195, 500);
  await page.waitForTimeout(250);
  samples.push(await page.evaluate(() => {
    const i = window.__birdy.renderer.info;
    return { calls: i.render.calls, tris: i.render.triangles, geos: i.memory.geometries, tex: i.memory.textures };
  }));
}
const max = (k) => Math.max(...samples.map((s) => s[k]));
console.log(JSON.stringify({ firstFrameMs: firstFrame, maxDrawCalls: max('calls'), maxTriangles: max('tris'), geometries: max('geos'), textures: max('tex'), errors }, null, 2));
await browser.close();
