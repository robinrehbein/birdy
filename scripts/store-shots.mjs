// Store screenshots (1080×1920): real game frames, framed on a sky
// background with a claim on top. Game time is advanced with the autopilot
// and invincibility ("god", test-only), nothing in the HUD is faked.
//   npm run build && npx vite preview --port 4173 &  node scripts/store-shots.mjs [de|en] [url]
// Writes docs/store/<lang>/screenshot-*.png.
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

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
const RAW = fs.mkdtempSync(path.join(os.tmpdir(), 'birdy-shots-'));
fs.mkdirSync(OUT, { recursive: true });

const CLAIMS = {
  de: {
    menu: ['Tippen. Ausweichen.', 'Durchfliegen.', 'Ohne Werbung · offline'],
    park: ['Drei Spuren,', 'ein Finger'],
    herbstwald: ['Vier Zonen mit', 'eigener Musik'],
    kaktus: ['Vorsicht,', 'Stachelkaktus!'],
    regenbogen: ['Regenbogen, Magnet', 'und Mini-Vogel'],
    shop: ['Bau dir deinen', 'eigenen Vogel'],
    welten: ['Neue Welten', 'freispielen'],
  },
  en: {
    menu: ['Tap. Dodge.', 'Fly through.', 'No ads · works offline'],
    park: ['Three lanes,', 'one finger'],
    herbstwald: ['Four zones with', 'their own music'],
    kaktus: ['Watch out for', 'the spiky cactus!'],
    regenbogen: ['Rainbow, magnet', 'and mini bird'],
    shop: ['Build your', 'own bird'],
    welten: ['Unlock', 'new worlds'],
  },
}[LANG];

const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2.4, locale: LANG === 'de' ? 'de-DE' : 'en-US' });
await page.addInitScript((lang) => {
  let s = 4242;
  Math.random = () => {
    s = (s + 0x6d2b79f5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  localStorage.setItem('birdy-lang', lang);
  localStorage.setItem('birdy-progress', JSON.stringify({
    coins: 2400, best: 32, runs: 6, tutorialDone: true, achieved: ['score10', 'score25', 'unlock5'],
    items: { skin: ['sunny', 'sky', 'cardinal'], hat: ['none', 'party', 'crown'], eyes: ['normal', 'shades'], trail: ['none', 'sparkle'] },
    equip: { skin: 'sunny', hat: 'party', eyes: 'shades', trail: 'sparkle' },
  }));
  window.__pilot = () => {
    const B = window.__birdy;
    const st = B.state;
    if (st.mode !== 'playing' || st.hold) return;
    const nx = B.gates.filter((g) => g.active && !g.passed).sort((a, b) => b.group.position.z - a.group.position.z)[0];
    if (!nx) { if (st.y < 5 && st.vy < 2) st.vy = 11.5; return; }
    if (nx.lanes[st.lane].blocked) st.lane = nx.lanes.findIndex((l) => !l.blocked);
    const L = nx.lanes[st.lane];
    const aim = (L.gapLow + L.gapHigh) / 2 + (L.hasPlant ? 0.6 : 0);
    if (st.y < aim - 0.7 && st.vy < 2) { st.vy = 11.5; st.squash = 1; }
  };
  window.__run = (sec, until) => {
    const fn = until ? new Function(`const B = window.__birdy; return (${until});`) : null;
    for (let t = 0; t < sec; t += 1 / 30) {
      window.__pilot();
      window.__birdy.advance(1 / 30);
      if (fn && fn()) return true;
    }
    return !fn;
  };
}, LANG);
await page.goto(URL);
await page.waitForFunction(() => window.__birdy?.renderer?.info.render.frame > 1);
await page.evaluate(() => window.__birdy.freeze(true));
const run = (sec, until) => page.evaluate(([s, u]) => window.__run(s, u), [sec, until]);
// No toasts, finished entrance animations, and a store frame has no
// language button (it is part of the menu, not the game).
async function raw(name) {
  await page.evaluate(() => {
    document.getElementById('toast').classList.remove('show');
    document.getElementById('lang-btn').style.visibility = 'hidden';
    for (const a of document.getAnimations()) if (a.effect?.getTiming().iterations !== Infinity) a.finish();
  });
  await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
  await page.screenshot({ path: `${RAW}/${name}.png` });
  await page.evaluate(() => { document.getElementById('lang-btn').style.visibility = ''; });
}

await run(1.5);
await raw('menu');
await page.click('#play-btn');
await page.mouse.click(195, 500);
await page.evaluate(() => { window.__birdy.state.god = true; });
await run(9);
await raw('park');
await run(90, 'B.state.zone >= 1');
await run(3);
await raw('herbstwald');
await run(90, 'B.state.zone >= 2');
await run(2);
// A row with a fully risen cactus next to the bird (a real gate, set up
// directly because waiting for a well-framed one takes very long).
await page.evaluate(() => {
  const B = window.__birdy;
  const g = B.gates.filter((x) => x.active && !x.passed).sort((a, b) => b.group.position.z - a.group.position.z)[0];
  g.configure(-5.5, [{ center: 5, size: 5 }, { center: 5.4, size: 5, plant: true, plantOffset: 0 }, { center: 5, size: 5 }]);
  B.state.lane = 0;
  B.state.x = -3;
  B.state.y = 5.2;
  B.state.time = (2.95 * 60) / 124 + Math.ceil(B.state.time / (240 / 124)) * (240 / 124) - 1 / 30;
  B.advance(1 / 30);
});
await raw('kaktus');
await run(1);
await page.evaluate(() => window.__birdy.activatePower('star'));
await run(1.5);
await raw('regenbogen');
await page.evaluate(() => { window.__birdy.handleBack(() => {}); window.__birdy.handleBack(() => {}); });
await run(1);
await page.click('#shop-btn');
await page.click('.tab[data-tab="skin"]');
await page.click('.skin[data-id="cardinal"]');
await page.click('#shop-action');
await page.click('.tab[data-tab="hat"]');
await page.click('.skin[data-id="crown"]');
await run(2.5);
await raw('shop');
await page.click('.tab[data-tab="world"]');
await page.click('.skin[data-id="candy"]');
await run(3);
await raw('welten');

// Framing: 1080×1920, sky with sun rays, claim on top, the frame below in a
// rounded plum border. Drawn with HTML/CSS in the same browser.
const font = fs.readFileSync('src/fonts/lilita-one-latin.woff2').toString('base64');
const frame = await browser.newPage({ viewport: { width: 1080, height: 1920 } });
const order = ['menu', 'park', 'herbstwald', 'kaktus', 'regenbogen', 'shop', 'welten'];
for (const [i, name] of order.entries()) {
  const img = fs.readFileSync(`${RAW}/${name}.png`).toString('base64');
  const [a, b, sub] = CLAIMS[name];
  await frame.setContent(`<!doctype html><html><head><style>
    @font-face { font-family: 'Lilita One'; src: url(data:font/woff2;base64,${font}); }
    html, body { margin: 0; width: 1080px; height: 1920px; overflow: hidden; }
    body { font-family: 'Lilita One'; color: #fff; text-align: center;
      background: repeating-conic-gradient(from 0deg at 50% 12%, rgba(255,255,255,0.12) 0 11.25deg, transparent 11.25deg 22.5deg),
        radial-gradient(circle at 50% 12%, rgba(255,250,220,0.8), transparent 45%),
        linear-gradient(#3f8fe0, #8fd3f0 55%, #ffe2b0); }
    h1 { margin: 0; padding-top: 70px; font-size: 104px; line-height: 1.02; font-weight: normal;
      -webkit-text-stroke: 16px #543847; paint-order: stroke fill; text-shadow: 0 10px 0 #543847; }
    h1 span { display: block; }
    h1 span + span { color: #fcb800; }
    .sub { display: inline-block; margin-top: 18px; font-size: 44px; padding: 8px 30px; border-radius: 999px;
      background: #543847; color: #fff6d5; }
    .phone { position: absolute; left: 50%; bottom: -60px; transform: translateX(-50%);
      width: 660px; border: 16px solid #543847; border-radius: 72px; overflow: hidden;
      box-shadow: 0 24px 0 rgba(84, 56, 71, 0.35); background: #543847; }
    .phone img { display: block; width: 100%; }
  </style></head><body>
    <h1><span>${a}</span><span>${b}</span></h1>${sub ? `<div class="sub">${sub}</div>` : ''}
    <div class="phone"><img src="data:image/png;base64,${img}"></div>
  </body></html>`);
  await frame.evaluate(() => document.fonts.ready);
  await frame.screenshot({ path: `${OUT}/screenshot-${i + 1}-${name}.png` });
}
await browser.close();
fs.rmSync(RAW, { recursive: true, force: true });
console.log('done');
