// JS reference shots for the native port: the same named, scripted states as
// native/screenshots (Shots.kt), taken from the original game with Playwright
// (SwiftShader WebGL) at 412x915 CSS px, devicePixelRatio 2.625 (1081x2402).
//   node scripts/native-shots/js-reference.mjs [filter]
// Builds the web app into native/build/js-dist, serves it locally and writes
// native/build/shots-js/NN-name.png plus menu-frames.json (the measured
// title/panel band per menu, the native fallback for the bird framing).
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
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
const DIST = path.join(ROOT, 'native/build/js-dist');
const OUT = path.join(ROOT, 'native/build/shots-js');
const FILTER = process.argv[2] || '';
fs.mkdirSync(OUT, { recursive: true });
execSync(`npx vite build --outDir ${DIST} --emptyOutDir --logLevel warn`, { cwd: ROOT, stdio: 'inherit' });

const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.woff2': 'font/woff2', '.png': 'image/png', '.svg': 'image/svg+xml', '.json': 'application/json', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  const p = path.join(DIST, decodeURIComponent(req.url.split('?')[0]).replace(/\/$/, '/index.html'));
  if (!p.startsWith(DIST) || !fs.existsSync(p)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': TYPES[path.extname(p)] || 'application/octet-stream' });
  fs.createReadStream(p).pipe(res);
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const BASE = `http://127.0.0.1:${server.address().port}/`;

// Same saves as ShotGame.kt / Shots.kt.
const STORE_SAVE = {
  coins: 2400, best: 32, runs: 6, tutorialDone: true, achieved: ['score10', 'score25', 'unlock5'],
  items: { skin: ['sunny', 'sky', 'cardinal'], hat: ['none', 'party', 'crown'], eyes: ['normal', 'shades'], trail: ['none', 'sparkle'] },
  equip: { skin: 'sunny', hat: 'party', eyes: 'shades', trail: 'sparkle' },
};
const wearing = (skin, trail, hat = 'none') => ({
  coins: 2400, best: 32, runs: 6, tutorialDone: true, achieved: ['score10', 'score25'],
  items: { skin: ['sunny', skin], trail: ['none', trail], hat: ['none', hat] }, equip: { skin, trail, hat },
});

const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const menuFrames = {};

async function session(save, lang, script) {
  const page = await browser.newPage({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 2.625, locale: lang === 'de' ? 'de-DE' : 'en-US' });
  page.on('pageerror', (e) => console.error('pageerror', e.message));
  await page.addInitScript(([save, lang]) => {
    let s = 4242;
    Math.random = () => {
      s = (s + 0x6d2b79f5) | 0;
      let t = Math.imul(s ^ (s >>> 15), 1 | s);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
    const fixed = 1790000000000; // same wall clock as the native FakeClock
    Date.now = () => fixed;
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
    window.__run = (sec, until) => {
      const fn = until ? new Function(`const B = window.__birdy; return (${until});`) : null;
      for (let t = 0; t < sec; t += 1 / 30) {
        window.__pilot();
        window.__birdy.advance(1 / 30);
        if (fn && fn()) return true;
      }
      return !fn;
    };
  }, [save, lang]);
  await page.goto(BASE);
  await page.waitForFunction(() => window.__birdy?.renderer?.info.render.frame > 1);
  await page.evaluate(() => window.__birdy.freeze(true));
  const ctx = {
    page,
    run: (sec, until) => page.evaluate(([s, u]) => window.__run(s, u), [sec, until]),
    advance: (sec) => page.evaluate((s) => window.__birdy.advance(s), sec),
    eval: (fn, arg) => page.evaluate(fn, arg),
    click: (sel) => page.click(sel),
    async startRun() {
      await page.click('#play-btn');
      await page.mouse.click(206, 550);
      await page.evaluate(() => { window.__birdy.state.god = true; });
    },
    async shot(name) {
      if (FILTER && !name.includes(FILTER)) return;
      await page.evaluate(() => {
        for (const a of document.getAnimations()) if (a.effect?.getTiming().iterations !== Infinity) a.finish();
      });
      await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
      // Menu band as measured by main.js measureMenuFrame (fractions of the app height).
      const frame = await page.evaluate(() => {
        const st = window.__birdy.state;
        if (st.mode !== 'ready') return null;
        const wrap = document.getElementById(st.menu === 'shop' ? 'shop' : st.menu === 'achievements' ? 'achievements' : 'start');
        const title = wrap.querySelector('.menu-title');
        const panel = wrap.querySelector('.panel');
        const box = document.getElementById('app').getBoundingClientRect();
        if (!title || !panel) return null;
        return { menu: st.menu, titleBottom: (title.getBoundingClientRect().bottom - box.top) / box.height, panelTop: (panel.getBoundingClientRect().top - box.top) / box.height };
      });
      if (frame) menuFrames[`${name}:${frame.menu}`] = frame;
      await page.screenshot({ path: path.join(OUT, `${name}.png`) });
      console.log('wrote', name);
    },
  };
  await script(ctx);
  await page.close();
}

const menuStart = async (c) => {
  await c.run(1.5);
  await c.shot('01-menu');
  await c.click('#shop-btn');
  await c.click('.tab[data-tab="skin"]');
  await c.click('.skin[data-id="cardinal"]');
  await c.click('#shop-action');
  await c.run(2.5);
  await c.shot('02-shop-skins');
  await c.click('.tab[data-tab="hat"]');
  await c.click('.skin[data-id="crown"]');
  await c.run(2.5);
  await c.shot('03-shop-hats');
  await c.click('.tab[data-tab="world"]');
  await c.click('.skin[data-id="candy"]');
  await c.run(3);
  await c.shot('04-shop-worlds');
  await c.click('.tab[data-tab="upgrade"]');
  await c.run(1.5);
  await c.shot('05-shop-upgrades');
};

await session(STORE_SAVE, 'de', menuStart);
// 06-shop-store needs ads and billing, which the web build does not have.
await session(STORE_SAVE, 'de', async (c) => {
  await c.click('#ach-btn');
  await c.run(2);
  await c.shot('07-achievements');
});
await session(STORE_SAVE, 'de', async (c) => {
  await c.startRun();
  await c.run(9);
  await c.shot('08-run-park');
  await c.run(90, 'B.state.zone >= 1');
  await c.run(3);
  await c.shot('09-zone-2');
  await c.run(90, 'B.state.zone >= 2');
  await c.run(2);
  await c.shot('10-zone-3');
  await c.eval(() => {
    const B = window.__birdy;
    const g = B.gates.filter((x) => x.active && !x.passed).sort((a, b) => b.group.position.z - a.group.position.z)[0];
    g.configure(-5.5, [{ center: 5, size: 5 }, { center: 5.4, size: 5, plant: true, plantOffset: 0 }, { center: 5, size: 5 }]);
    B.state.lane = 0;
    B.state.x = -3;
    B.state.y = 5.2;
    B.state.time = (2.95 * 60) / 124 + Math.ceil(B.state.time / (240 / 124)) * (240 / 124) - 1 / 30;
    B.advance(1 / 30);
  });
  await c.shot('11-cactus');
  await c.run(90, 'B.state.zone >= 3');
  await c.run(3);
  await c.shot('12-zone-4');
  await c.eval(() => {
    const B = window.__birdy;
    B.state.god = false;
    for (const k of Object.keys(B.state.power)) B.state.power[k] = 0;
    B.state.grace = 0;
    for (let t = 0; t < 10 && B.state.mode === 'playing'; t += 1 / 30) { B.state.vy = -22; B.advance(1 / 30); }
    B.advance(0.1);
  });
  await c.shot('16-crash');
  await c.advance(1.5);
  await c.shot('17-gameover');
});
for (const [type, name] of [['star', '13-power-star'], ['magnet', '14-power-magnet'], ['mini', '15-power-mini']]) {
  await session(STORE_SAVE, 'de', async (c) => {
    await c.startRun();
    await c.run(5);
    await c.eval((t) => window.__birdy.activatePower(t), type);
    await c.run(1.5);
    await c.shot(name);
  });
}
await session(STORE_SAVE, 'de', async (c) => {
  await c.startRun();
  await c.run(4);
  await c.eval(() => window.__birdy.handleBack(() => {}));
  await c.advance(0.5);
  await c.shot('18-pause');
});
await session(null, 'de', async (c) => {
  await c.advance(1);
  await c.shot('19-tutorial-flap');
  await c.page.mouse.click(206, 550);
  await c.eval(() => {
    const B = window.__birdy;
    for (let t = 0; t < 30 && B.state.mode === 'playing'; t += 1 / 30) {
      if (document.getElementById('hand').classList.contains('side')) break;
      if (B.state.y < 5.2 && B.state.vy < 2) { B.state.vy = 11.5; B.state.squash = 1; }
      B.advance(1 / 30);
    }
    B.advance(0.6);
  });
  await c.shot('20-tutorial-dodge');
});
for (const [save, name] of [[wearing('galaxy', 'rainbow'), '21-skin-galaxy-rainbow'], [wearing('gold', 'fire', 'crown'), '22-skin-gold-fire']]) {
  await session(save, 'de', async (c) => {
    await c.startRun();
    await c.run(4);
    await c.shot(name);
  });
}
await session(wearing('diamond', 'stardust', 'halo'), 'de', async (c) => {
  await c.run(1.5);
  await c.click('#shop-btn');
  await c.click('.tab[data-tab="trail"]');
  await c.run(2.5);
  await c.shot('23-shop-trail-diamond');
});
await session(STORE_SAVE, 'en', async (c) => {
  await c.run(1.5);
  await c.shot('24-menu-en');
});

fs.writeFileSync(path.join(OUT, 'menu-frames.json'), JSON.stringify(menuFrames, null, 2));
await browser.close();
server.close();
console.log('done');
