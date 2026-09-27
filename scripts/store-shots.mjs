// Store screenshots (1080x1920) from one real run. Nothing in the HUD is
// faked: an autopilot with invincibility ("god", test-only flag) keeps the
// bird alive and the script waits until each scene actually happens
// (zone banner, piranha plants in the canyon, rainbow power-up).
//   npm run build && npx vite preview --port 4173 &  node scripts/store-shots.mjs [de|en] [url]
// Writes docs/store/<lang>/screenshot-*.png.
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
const LANG = process.argv[2] || 'de';
const URL = process.argv[3] || 'http://localhost:4173/';
const OUT = `docs/store/${LANG}`;
fs.mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 360, height: 640 }, deviceScaleFactor: 3, locale: LANG === 'de' ? 'de-DE' : 'en-US' });
await page.addInitScript((lang) => {
  localStorage.setItem('birdy-lang', lang);
  localStorage.setItem('birdy-progress', JSON.stringify({
    coins: 2400, best: 32, runs: 6, tutorialDone: true, achieved: ['score10', 'score25', 'unlock5'],
    items: { skin: ['sunny', 'sky', 'cardinal'], hat: ['none', 'party', 'crown'], eyes: ['normal', 'shades'], trail: ['none', 'sparkle'] },
    equip: { skin: 'sunny', hat: 'party', eyes: 'shades', trail: 'sparkle' },
  }));
}, LANG);
await page.goto(URL);
await page.waitForTimeout(4000);
await page.screenshot({ path: `${OUT}/screenshot-1-menu.png` });
// Wait until no toast (achievement etc.) has been showing for a moment.
async function noToast() {
  for (let calm = 0; calm < 6;) {
    await page.waitForTimeout(500);
    calm = (await page.evaluate(() => document.getElementById('toast').classList.contains('show'))) ? 0 : calm + 1;
  }
}
const until = (fn, arg, timeout = 400000) => page.waitForFunction(fn, arg, { timeout, polling: 100 });

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
    if (!nx) { if (s.y < 5 && s.vy < 2) s.vy = 11.5; return; }
    if (nx.lanes[s.lane].blocked) s.lane = nx.lanes.findIndex((l) => !l.blocked);
    const L = nx.lanes[s.lane];
    const aim = (L.gapLow + L.gapHigh) / 2 + (L.hasPlant ? 0.6 : 0);
    if (s.y < aim - 0.7 && s.vy < 2) { s.vy = 11.5; s.squash = 1; }
  }, 30);
});

await until(() => window.__birdy.state.score >= 6);
await page.screenshot({ path: `${OUT}/screenshot-2-park.png` });

await until(() => window.__birdy.state.zone >= 1);
await page.waitForTimeout(700);
await page.screenshot({ path: `${OUT}/screenshot-3-herbstwald.png` });

// A spiky cactus popped up close enough to see (canyon or later).
await until(() => window.__birdy.state.zone >= 2);
await until(() => {
  const B = window.__birdy;
  const plantNear = B.gates.some((g) => g.active && !g.passed && g.group.position.z > -24 && g.group.position.z < -10
    && g.lanes.some((l) => l.hasPlant && l.plant.group.visible && l.plant.group.position.y > l.gapLow - 1.2));
  // No coin right in front of the camera hiding the bird.
  const coinInFront = B.coins.some((c) => c.active && c.mesh.position.z > -9 && Math.abs(c.mesh.position.x - B.state.x) < 2);
  return plantNear && !coinInFront && B.state.power.mini <= 0;
});
await page.screenshot({ path: `${OUT}/screenshot-4-kaktus.png` });

// Rainbow power-up (the real effect, triggered as if picked up).
await page.evaluate(() => window.__birdy.activatePower('star'));
await page.waitForTimeout(2500);
await page.screenshot({ path: `${OUT}/screenshot-5-regenbogen.png` });

// Shop: the bird workshop (hats) with a preview, then a world preview.
await page.evaluate(() => { window.__birdy.handleBack(() => {}); window.__birdy.handleBack(() => {}); });
await page.waitForTimeout(800);
await page.click('#shop-btn');
await page.click('.tab[data-tab="skin"]');
await page.click('.skin[data-id="cardinal"]');
await page.click('#shop-action');
await page.click('.tab[data-tab="hat"]');
await page.click('.skin[data-id="crown"]');
await page.waitForTimeout(6000);
await noToast();
await page.screenshot({ path: `${OUT}/screenshot-6-shop.png` });
await page.click('.tab[data-tab="world"]');
await page.click('.skin[data-id="candy"]');
await page.waitForTimeout(12000);
await noToast();
await page.screenshot({ path: `${OUT}/screenshot-7-welten.png` });
await browser.close();
console.log('done');
