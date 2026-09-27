// Store screenshots (1080x1920) from real gameplay scenes.
//   npm run build && npx vite preview --port 4173 &  node scripts/store-shots.mjs
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
const OUT = 'docs/store';
fs.mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 360, height: 640 }, deviceScaleFactor: 3 });
await page.goto('http://localhost:4173/');
await page.evaluate(() => localStorage.setItem('birdy-progress', JSON.stringify({
  coins: 640, best: 32, runs: 6, owned: ['sunny', 'sky', 'cardinal'], skin: 'sunny', tutorialDone: true,
})));
await page.reload();
await page.waitForTimeout(2500);
await page.screenshot({ path: `${OUT}/screenshot-1-menu.png` });

// Autopilot for clean gameplay shots: fly through the gap of an open lane.
async function play() {
  await page.click('#play-btn');
  await page.waitForTimeout(300);
  await page.mouse.click(180, 400);
  await page.evaluate(() => {
    const B = window.__birdy;
    B.state.god = true;
    setInterval(() => {
      const s = B.state;
      if (s.mode !== 'playing' || s.hold) return;
      const nx = B.gates.filter((g) => g.active && !g.passed).sort((a, b) => b.group.position.z - a.group.position.z)[0];
      if (!nx) return;
      if (nx.lanes[s.lane].blocked) s.lane = nx.lanes.findIndex((l) => !l.blocked);
      const L = nx.lanes[s.lane];
      const aim = (L.gapLow + L.gapHigh) / 2;
      if (s.y < aim - 0.7 && s.vy < 2) { s.vy = 11.5; s.squash = 1; }
    }, 30);
  });
}
await play();
await page.waitForTimeout(14000);
// A believable mid-run HUD for the listing.
await page.evaluate(() => {
  const B = window.__birdy;
  B.state.score = 17;
  B.state.coins = 12;
  document.getElementById('score').textContent = '17';
  document.getElementById('coin-count').textContent = '12';
});
await page.screenshot({ path: `${OUT}/screenshot-2-park.png` });
for (const [zone, name] of [[1, 'herbstwald'], [2, 'canyon'], [3, 'bluetenhain']]) {
  await page.evaluate((z) => { window.__birdy.state.gatesSpawned = z * 15 + 1; window.__birdy.enterZone(z); }, zone);
  // Let the new scenery stream in completely (headless runs slowly).
  await page.waitForTimeout(zone === 1 ? 1400 : 60000);
  await page.screenshot({ path: `${OUT}/screenshot-${2 + zone}-${name}.png` });
}
await page.evaluate(() => window.__birdy.handleBack(() => {}));
await page.evaluate(() => window.__birdy.handleBack(() => {}));
await page.waitForTimeout(800);
await page.evaluate(() => window.__birdy.openShop(true));
await page.click('.skin[data-id="flamingo"]');
await page.waitForTimeout(2500);
await page.screenshot({ path: `${OUT}/screenshot-6-shop.png` });
await browser.close();
console.log('done');
