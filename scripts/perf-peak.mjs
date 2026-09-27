// Worst-case render budget: a long autopilot run (all zones, cacti, all
// power-ups at once, crash) sampled every half second of game time.
//   node scripts/perf-peak.mjs [url=http://localhost:4173/]
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
const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
await page.addInitScript(() => localStorage.setItem('birdy-progress', JSON.stringify({ runs: 3, tutorialDone: true })));
await page.goto(url);
await page.waitForFunction(() => window.__birdy?.renderer?.info.render.frame > 1);
await page.evaluate(() => window.__birdy.freeze(true));
await page.click('#play-btn');
await page.mouse.click(195, 500);
const sample = () => page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(() => {
  const i = window.__birdy.renderer.info.render;
  r({ calls: i.calls, tris: i.triangles });
}))));
const max = { calls: 0, tris: 0 };
for (let k = 0; k < 150; k++) {
  await page.evaluate((k2) => {
    const B = window.__birdy;
    const st = B.state;
    st.god = true;
    if (k2 % 20 === 5) ['star', 'magnet', 'mini'].forEach((t) => B.activatePower(t));
    for (let t = 0; t < 0.5; t += 1 / 30) {
      if (st.mode === 'playing' && st.y < 5 && st.vy < 2) st.vy = 11.5;
      B.advance(1 / 30);
    }
  }, k);
  const s = await sample();
  max.calls = Math.max(max.calls, s.calls);
  max.tris = Math.max(max.tris, s.tris);
}
// The crash moment (bonk star, feathers).
await page.evaluate(() => { const B = window.__birdy; B.state.god = false; for (let t = 0; t < 20 && B.state.mode === 'playing'; t += 1 / 30) B.advance(1 / 30); B.advance(0.05); });
const c = await sample();
max.calls = Math.max(max.calls, c.calls);
max.tris = Math.max(max.tris, c.tris);
console.log(JSON.stringify({ peakDrawCalls: max.calls, peakTriangles: max.tris }));
await browser.close();
