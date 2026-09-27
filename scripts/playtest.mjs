// Automated bot playtest. Runs the game headless (?sim) at a fixed 60 Hz step
// and prints per-skill statistics: run length, score, death causes and where
// in the run players die (difficulty curve).
//
//   npm run build && npx vite preview --port 4173 &
//   node scripts/playtest.mjs [runs=50] [url=http://localhost:4173/]
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';

const require = createRequire(import.meta.url);
let chromium;
try {
  ({ chromium } = require('playwright'));
} catch {
  const root = execSync('npm root -g').toString().trim();
  ({ chromium } = require(`${root}/playwright`));
}

const runs = Number(process.argv[2] || 50);
const url = process.argv[3] || 'http://localhost:4173/';
const skills = (process.env.SKILLS || 'novice,good,pro').split(',');

const browser = await chromium.launch({
  args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
await page.goto(`${url}?sim`);
await page.waitForFunction(() => window.__birdy?.simulate);

const median = (a) => {
  const s = [...a].sort((x, y) => x - y);
  return s.length ? s[Math.floor(s.length / 2)] : 0;
};
const pct = (a, p) => {
  const s = [...a].sort((x, y) => x - y);
  return s.length ? s[Math.min(s.length - 1, Math.floor(s.length * p))] : 0;
};

const report = {};
for (const skill of skills) {
  const t0 = Date.now();
  const res = await page.evaluate((o) => window.__birdy.simulate(o), { runs, bot: { skill }, maxTime: Number(process.env.MAX_TIME || 180) });
  console.error(`${skill}: ${runs} runs in ${((Date.now() - t0) / 1000).toFixed(1)}s`);
  const times = res.map((r) => r.time);
  const scores = res.map((r) => r.score);
  const causes = {};
  for (const r of res) causes[r.cause] = (causes[r.cause] || 0) + 1;
  const buckets = {};
  for (const r of res) {
    const b = `${Math.floor(r.score / 10) * 10}-${Math.floor(r.score / 10) * 10 + 9}`;
    buckets[b] = (buckets[b] || 0) + 1;
  }
  report[skill] = {
    runs: res.length,
    time: { median: +median(times).toFixed(1), p10: +pct(times, 0.1).toFixed(1), p90: +pct(times, 0.9).toFixed(1) },
    score: { median: median(scores), p10: pct(scores, 0.1), p90: pct(scores, 0.9), max: Math.max(...scores) },
    coins: median(res.map((r) => r.coins)),
    // Share of runs that reached zone 2, 3 and 4 (index 1..3).
    zonesReached: [1, 2, 3].map((z) => `${Math.round((100 * res.filter((r) => (r.zone || 0) >= z).length) / res.length)}%`).join(' / '),
    causes,
    deathsByScore: buckets,
  };
}
report.errors = errors;
console.log(JSON.stringify(report, null, 2));
await browser.close();
