// Golden fixture that RUNS the actual src/progress.js (not a reimplementation)
// under a mocked localStorage/Date, to capture the save-migration and
// shop-equip-fallback edge cases in load() (lines 96-146) and a few of the
// stateful methods, exactly as the shipped JS computes them. Node has no
// localStorage/Date-mocking needed beyond a plain in-memory Storage stub;
// Date.now()/`new Date()` are NOT mocked (not needed for these scenarios —
// they don't depend on the wall-clock day), except where noted.
//
// Each scenario gets a FRESH module instance (progress.js has module-level
// singleton state `data = load()` evaluated at import time) by importing it
// with a unique cache-busting query string, which Node's ESM loader treats
// as a distinct module.
//
// Run: node scripts/native-golden/meta-progress-load.mjs
// Writes: docs/native/golden/meta-progress-load.json
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const KEY = 'birdy-progress';
const progressUrl = new URL('../../src/progress.js', import.meta.url).href;

function mockStorage(initial = {}) {
  const store = new Map(Object.entries(initial));
  return {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, String(v)),
    removeItem: (k) => store.delete(k),
    _dump: () => Object.fromEntries(store),
  };
}

let seq = 0;
async function freshProgress(initialStorage) {
  global.localStorage = mockStorage(initialStorage);
  const mod = await import(`${progressUrl}?case=${seq++}`);
  return { progress: mod.progress, storage: global.localStorage };
}

const out = { source: 'src/progress.js load() lines 96-146, and the shop/upgrade/achievement methods', scenarios: [] };

// --- Scenario 1: brand-new player (no localStorage entry at all) -----------
{
  const { progress, storage } = await freshProgress({});
  out.scenarios.push({
    name: 'fresh-install',
    coins: progress.coins, best: progress.best, runs: progress.runs,
    equippedSkin: progress.skin.id, equippedTrail: progress.trail.id,
    ownsExtraSkin: progress.owns('skin', 'sky'),
    tutorialDone: progress.tutorialDone,
    rewardedAdsLeft: progress.rewardedAdsLeft,
    savedShape: JSON.parse(storage.getItem(KEY)),
  });
}

// --- Scenario 2: pre-shop legacy save (only had owned/skin, trails/trail,
// no items/equip objects at all) migrates into items.*/equip.* ------------
{
  const legacy = { [KEY]: JSON.stringify({ coins: 250, best: 12, runs: 4, owned: ['sunny', 'sky'], skin: 'sky', trails: ['none', 'sparkle'], trail: 'sparkle' }) };
  const { progress, storage } = await freshProgress(legacy);
  out.scenarios.push({
    name: 'legacy-pre-shop-save-migration',
    coins: progress.coins,
    equippedSkin: progress.skin.id, equippedTrail: progress.trail.id,
    ownsSky: progress.permanentlyOwns('skin', 'sky'),
    ownsSparkleTrail: progress.permanentlyOwns('trail', 'sparkle'),
    // every OTHER kind (pattern/hat/eyes/beak/world/pipe) must have been
    // back-filled with just its free first item, equipped to that free item
    savedItems: JSON.parse(storage.getItem(KEY)).items,
    savedEquip: JSON.parse(storage.getItem(KEY)).equip,
  });
}

// --- Scenario 3: even older save with the ancient `birdy-best` key --------
{
  const old = { [KEY]: JSON.stringify({ coins: 10, best: 3, runs: 1 }), 'birdy-best': '77' };
  const { progress } = await freshProgress(old);
  out.scenarios.push({
    name: 'birdy-best-legacy-key-migration',
    note: 'load() does best = max(data.best, Number(localStorage.birdy-best)||0) — the OLD standalone key wins if higher',
    best: progress.best,
  });
}

// --- Scenario 4: equip points at an item no longer owned (corrupted save,
// or an item the schema removed) falls back to the kind's free item -------
{
  const corrupt = { [KEY]: JSON.stringify({ coins: 0, items: { skin: ['sunny'] }, equip: { skin: 'doesnotexist' } }) };
  const { progress } = await freshProgress(corrupt);
  out.scenarios.push({
    name: 'equip-points-to-unowned-item-falls-back-to-free',
    equippedSkin: progress.skin.id,
  });
}

// --- Scenario 5: style pass trial grants temporary access to ALL skins/
// worlds (not just the equipped one) but NOT to other kinds ----------------
{
  const trial = { [KEY]: JSON.stringify({ coins: 0, stylePassUntil: Date.now() + 3600000 }) };
  const { progress } = await freshProgress(trial);
  out.scenarios.push({
    name: 'style-pass-trial-access',
    ownsUnpurchasedSkin_night: progress.owns('skin', 'night'),
    ownsUnpurchasedWorld_candy: progress.owns('world', 'candy'),
    ownsUnpurchasedPattern_mask: progress.owns('pattern', 'mask'),
    ownsUnpurchasedPipe_gold: progress.owns('pipe', 'gold'),
    stylePassMinutesLeft: progress.stylePassMinutesLeft,
  });
}

// --- Scenario 6: paid (IAP) product ownership persists across kinds
// skin/world only (regex ^birdy_(skin|world)_([a-z]+)$) --------------------
{
  const paid = { [KEY]: JSON.stringify({ coins: 0, paidProducts: ['birdy_skin_gold', 'birdy_world_candy'] }) };
  const { progress } = await freshProgress(paid);
  out.scenarios.push({
    name: 'paid-product-ownership',
    // grantPaidProduct / permanentlyOwns check against CATALOG price>0 too
    ownsGoldSkin: progress.permanentlyOwns('skin', 'gold'),
    ownsCandyWorld: progress.permanentlyOwns('world', 'candy'),
    equippedSkinAfterLoad: progress.skin.id,
    note: 'paidProducts alone (from an external purchase-restore) does NOT auto-equip; equip[kind] is only forced to the paid item inside grantPaidProduct(), not in load(). load() DOES accept paidAccess as a reason to keep an already-equipped-but-unowned-in-items id (see load() line 135-137).',
  });
}

// --- Scenario 7: buy() / select() / grant() / buyUpgrade() basic flows ----
{
  const { progress } = await freshProgress({ [KEY]: JSON.stringify({ coins: 1000 }) });
  const buyOk = progress.buy('skin', 'sky'); // price 100
  const coinsAfterBuy = progress.coins;
  const selectOk = progress.select('skin', 'sky');
  const buyTwiceOk = progress.buy('skin', 'sky'); // already owned -> false
  const upgradePrice0 = progress.upgradePrice('star'); // 300
  progress.buyUpgrade('star');
  const levelAfter1 = progress.level('star');
  const upgradePrice1 = progress.upgradePrice('star'); // 800
  out.scenarios.push({
    name: 'buy-select-upgrade-flow',
    buyOk, coinsAfterBuy, selectOk, buyTwiceOk,
    upgradePrice0, levelAfter1, upgradePrice1,
  });
}

// --- Scenario 8: insufficient coins fails buy/upgrade cleanly --------------
{
  const { progress } = await freshProgress({ [KEY]: JSON.stringify({ coins: 5 }) });
  out.scenarios.push({
    name: 'insufficient-coins',
    buySky: progress.buy('skin', 'sky'),
    coinsUnchanged: progress.coins,
    buyUpgradeStar: progress.buyUpgrade('star'),
  });
}

// --- Scenario 9: rewarded-ad slot accounting across the 3/day cap ---------
{
  const { progress } = await freshProgress({ [KEY]: JSON.stringify({ coins: 0 }) });
  const grants = [];
  for (let i = 0; i < 5; i++) grants.push(progress.grantRewardedCoins());
  out.scenarios.push({
    name: 'rewarded-ad-cap-3-per-day',
    grants, // expect [30,30,30,0,0]
    finalCoins: progress.coins,
    rewardedAdsLeft: progress.rewardedAdsLeft,
  });
}

// --- Scenario 10: giftClaim end-to-end via the real claimGift() ----------
{
  const { progress } = await freshProgress({ [KEY]: JSON.stringify({ coins: 0 }) });
  const first = progress.claimGift();
  const secondSameDay = progress.claimGift(); // null, already claimed today
  out.scenarios.push({
    name: 'claim-gift-same-day-blocked',
    first, secondSameDay,
    streakAfter: progress.streak,
  });
}

// --- Scenario 11: finishRun end-to-end (mission + achievement completion) -
{
  const { progress } = await freshProgress({ [KEY]: JSON.stringify({ coins: 0, best: 0 }) });
  const missionsBefore = progress.missions().map((m) => ({ id: m.id, goal: m.goal }));
  const run = { score: 999, coins: 50, zone: 5, bestChain: 20, powerups: 10, near: 20, plants: 200 };
  const result = progress.finishRun(run);
  out.scenarios.push({
    name: 'finishRun-completes-everything-with-a-huge-run',
    missionsBefore,
    isBest: result.isBest,
    completedMissionIds: result.completed.map((m) => m.id),
    unlockedAchievementIds: result.achievements.map((a) => a.id),
    coinsAfter: progress.coins,
    bestAfter: progress.best,
  });
}

writeFileSync(new URL('../../docs/native/golden/meta-progress-load.json', import.meta.url), JSON.stringify(out, null, 2));
console.log('wrote meta-progress-load.json');
