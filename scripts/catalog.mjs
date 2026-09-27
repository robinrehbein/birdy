// Visual catalog: screenshots of every screen and state, per language and
// screen size, for the art-direction reviews in docs/VISUAL.md.
//
//   npm run build && npx vite preview --port 4173 &
//   node scripts/catalog.mjs [out=catalog] [url=http://localhost:4173/]
//
// Env: LANGS=de,en  SIZES=360x640,390x844  ONLY=<regex of shot names>  DPR=2
// Writes <out>/<lang>-<w>x<h>/<shot>.png. Game time is advanced without
// drawing (window.__birdy.advance), Math.random is seeded, so the same
// script on two builds gives comparable scenes.
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
const OUT = process.argv[2] || 'catalog';
const URL = process.argv[3] || 'http://localhost:4173/';
const LANGS = (process.env.LANGS || 'de,en').split(',');
const SIZES = (process.env.SIZES || '360x640,390x844').split(',').map((s) => s.split('x').map(Number));
const ONLY = process.env.ONLY ? new RegExp(process.env.ONLY) : null;
const DPR = Number(process.env.DPR || 2);

const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const errors = [];

// Seeded Math.random plus a test autopilot (keeps the bird in the gaps).
function initScript({ lang, save }) {
  let s = 12345;
  Math.random = () => {
    s = (s + 0x6d2b79f5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  localStorage.setItem('birdy-lang', lang);
  if (save) localStorage.setItem('birdy-progress', JSON.stringify(save));
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
  // Advance game time with the autopilot, until `until` (a function body) is true.
  window.__run = (sec, until) => {
    const fn = until ? new Function(`const B = window.__birdy; return (${until});`) : null;
    for (let t = 0; t < sec; t += 1 / 30) {
      window.__pilot();
      window.__birdy.advance(1 / 30);
      if (fn && fn()) return true;
    }
    return !fn;
  };
}

const RETURNING = {
  coins: 2400, best: 32, runs: 6, tutorialDone: true, achieved: ['score10', 'score25', 'unlock5'],
  items: { skin: ['sunny', 'sky', 'cardinal'], hat: ['none', 'party', 'crown'], eyes: ['normal', 'shades'], trail: ['none', 'sparkle'] },
  equip: { skin: 'sunny', hat: 'party', eyes: 'shades', trail: 'sparkle' },
};

async function session(lang, [w, h], save) {
  const page = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: DPR, locale: lang === 'de' ? 'de-DE' : 'en-US' });
  page.on('pageerror', (e) => errors.push(`${lang} ${w}x${h}: ${e.message}`));
  await page.addInitScript(initScript, { lang, save });
  await page.goto(URL);
  await page.waitForFunction(() => window.__birdy?.renderer?.info.render.frame > 1);
  const dir = `${OUT}/${lang}-${w}x${h}`;
  fs.mkdirSync(dir, { recursive: true });
  const run = (sec, until) => page.evaluate(([s, u]) => window.__run(s, u), [sec, until]);
  const shot = async (name, settleMs = 250) => {
    if (ONLY && !ONLY.test(name)) return;
    await page.waitForTimeout(settleMs); // CSS animations run on real time
    await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
    await page.screenshot({ path: `${dir}/${name}.png` });
  };
  return { page, run, shot };
}

for (const lang of LANGS) {
  for (const size of SIZES) {
    const t0 = Date.now();
    // --- First launch: guided tutorial -------------------------------------
    {
      const { page, run, shot } = await session(lang, size, null);
      await run(1.2);
      await shot('01-tutorial-flap', 500);
      await page.mouse.click(size[0] / 2, size[1] * 0.6);
      await page.evaluate(() => { window.__pilot = () => { const st = window.__birdy.state; if (st.y < 5 && st.vy < 2) st.vy = 11.5; }; });
      await run(40, "document.getElementById('hand').classList.contains('side')");
      await run(0.5);
      await shot('02-tutorial-switch', 300);
      await page.close();
    }
    // --- Returning player: menus, shop, runs --------------------------------
    const { page, run, shot } = await session(lang, size, RETURNING);
    await run(1.5);
    await shot('03-menu', 600);
    await page.click('#ach-btn');
    await run(1);
    await shot('04-achievements', 500);
    await page.click('#ach-back');

    await page.click('#shop-btn');
    const tabs = [['skin', 'cardinal'], ['pattern', 'spots'], ['hat', 'crown'], ['eyes', 'shades'], ['beak', 'toucan'],
      ['trail', 'sparkle'], ['world', 'candy'], ['pipe', 'wood'], ['upgrade', 'magnet']];
    for (const [i, [tab, item]] of tabs.entries()) {
      await page.click(`.tab[data-tab="${tab}"]`);
      await page.click(`.skin[data-id="${item}"]`);
      await run(2);
      await shot(`05-shop-${i + 1}-${tab}`, 400);
    }
    await page.click('#shop-back');
    await run(1);

    // Run: get ready, zone 1, HUD, toasts, pickups; then the natural zone
    // changes (banner + scene), one power-up per zone.
    await page.click('#play-btn');
    await run(1);
    await shot('06-run-ready', 300);
    await page.mouse.click(size[0] / 2, size[1] * 0.6);
    await page.evaluate(() => { window.__birdy.state.god = true; });
    await run(5);
    await shot('07-run-zone1');
    await page.evaluate((de) => window.__birdy.toast(de ? '✓ Sammle 20 Münzen +40' : '✓ Collect 20 coins +40'), lang === 'de');
    await run(0.2);
    await shot('08-toast', 450);
    await run(2.5);
    await page.evaluate(() => {
      const B = window.__birdy;
      B.pickups.filter((p, i) => i % 2 === 0).forEach((p, i) => {
        p.active = true;
        p.group.visible = true;
        p.group.position.set([-3, 0, 3][i], 5.5, -16);
      });
    });
    await run(0.05);
    await shot('09-pickups');
    await page.evaluate(() => window.__birdy.pickups.forEach((p) => { p.active = false; p.group.visible = false; }));
    const zoneNames = ['park', 'autumn', 'canyon', 'blossom'];
    const powers = ['star', 'magnet', 'mini'];
    for (let z = 1; z <= 3; z++) {
      await run(90, `B.state.zone >= ${z}`);
      await run(0.2);
      await shot(`13-zone-banner-${z + 1}`, 350);
      await run(4);
      await shot(`14-run-zone${z + 1}-${zoneNames[z]}`);
      await page.evaluate((t) => window.__birdy.activatePower(t), powers[z - 1]);
      await run(1.2);
      await shot(`${9 + z}-power-${powers[z - 1]}`);
      await page.evaluate(() => { const p = window.__birdy.state.power; p.star = p.magnet = p.mini = 0.01; });
      await run(1.5);
    }
    // A spiky cactus up close.
    await run(60, `B.gates.some((g) => g.active && !g.passed && g.group.position.z > -22 && g.group.position.z < -12
      && g.lanes.some((l) => l.hasPlant && l.plant.group.visible && l.plant.group.position.y > l.gapLow - 1.2))`);
    await shot('15-cactus');

    // Shop worlds: a fresh run from the menu starts in the chosen world.
    for (const id of ['winter', 'beach', 'candy', 'mushroom']) {
      await page.evaluate((w) => {
        const B = window.__birdy;
        B.progress.grant('world', w);
        B.progress.select('world', w);
        B.handleBack(() => {});
        B.handleBack(() => {});
        // As after choosing it in the shop: the menu shows the world at once.
        B.openShop(true);
        B.openShop(false);
      }, id);
      await page.click('#play-btn');
      await page.mouse.click(size[0] / 2, size[1] * 0.6);
      await page.evaluate(() => { window.__birdy.state.god = true; });
      await run(6);
      await shot(`16-world-${id}`);
    }

    // Pause, crash, game over.
    await page.evaluate(() => window.__birdy.handleBack(() => {}));
    await shot('17-pause', 400);
    await page.mouse.click(size[0] / 2, size[1] / 2);
    await page.evaluate(() => { window.__birdy.state.god = false; window.__pilot = () => {}; });
    await run(20, "B.state.mode === 'dead'");
    await run(0.1);
    await shot('18-crash', 60);
    await run(1.5);
    await shot('19-gameover', 900);
    await page.close();
    console.error(`${lang} ${size.join('x')}: ${((Date.now() - t0) / 1000).toFixed(0)} s`);
  }
}
await browser.close();
if (errors.length) console.error('page errors:', errors);
console.log(errors.length ? 'done with errors' : 'done');
