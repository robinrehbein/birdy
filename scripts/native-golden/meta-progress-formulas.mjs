// Golden fixture for the small pure formulas in src/progress.js that are NOT
// the mission generator: the daily-gift streak/amount curve (lines 88-91,
// 320-321), rewarded-ad slot accounting (lines 92-93, 190-201), the style
// pass duration (line 94, 194-213), upgrade pricing (UPGRADES in catalog.js
// + progress.js lines 289-302), and the RUN_MAX_STATS/RUN_SUM_STATS/
// ACHIEVEMENTS tables (lines 12-37).
// Run: node scripts/native-golden/meta-progress-formulas.mjs
// Writes: docs/native/golden/meta-progress-formulas.json
import { writeFileSync } from 'node:fs';
import { UPGRADES, UPGRADE_MAX } from '../../src/catalog.js';

const out = { source: 'src/progress.js' };

// --- daily gift (lines 88-91, 313-332) --------------------------------------
const GIFT_BASE = 20;
const GIFT_STEP = 10;
const GIFT_MAX_STREAK = 7;
function giftAmount(streak) {
  return GIFT_BASE + GIFT_STEP * (Math.min(streak, GIFT_MAX_STREAK) - 1);
}
out.gift = {
  constants: { GIFT_BASE, GIFT_STEP, GIFT_MAX_STREAK },
  formula: 'amount = GIFT_BASE + GIFT_STEP * (min(streak, GIFT_MAX_STREAK) - 1), streak starts at 1',
  amountsByStreak: Object.fromEntries(Array.from({ length: 12 }, (_, i) => i + 1).map((s) => [s, giftAmount(s)])),
  availability: 'giftAvailable() = data.gift.last !== today() (one claim per calendar day, local device calendar day via dayKey())',
  streakRule: 'on claim: streak = (gift.last === yesterday) ? gift.streak + 1 : 1. If neither today nor yesterday was claimed, the getter `streak` (line 316-319) reports 0 (broken streak) even though data.gift.streak is not reset until the next claim.',
  streakGetterFormula: 'streak getter returns g.streak if g.last===today()||g.last===dayKey(-1), else 0 (does not mutate stored data)',
};

// --- rewarded ads / style pass (lines 92-94, 190-213) -----------------------
out.rewardedAds = {
  REWARDED_ADS_PER_DAY: 3,
  REWARDED_COINS: 30,
  rewardedAdsLeftFormula: 'max(0, 3 - count) where count = data.rewardedAds.count if data.rewardedAds.day===today() else 0 (count resets implicitly by being ignored, not zeroed, once the day rolls over)',
  useRewardedSlot: 'returns false if rewardedAdsLeft===0; else sets rewardedAds={day:today(), count: 3-rewardedAdsLeft+1} and returns true — one shared pool of 3/day covers BOTH the coin reward and the style-pass reward (grantStylePass also calls useRewardedSlot)',
};
const STYLE_PASS_MS = 60 * 60 * 1000;
out.stylePass = {
  STYLE_PASS_MS,
  STYLE_PASS_MINUTES: STYLE_PASS_MS / 60000,
  grantStylePass: 'no-op (returns false) if stylePassMinutesLeft>0 already, or if no rewarded slot left; else stylePassUntil = Date.now() + STYLE_PASS_MS (does NOT stack/extend, only (re)starts a fresh 60 min when expired)',
  stylePassMinutesLeftFormula: 'max(0, ceil((stylePassUntil - Date.now()) / 60000))',
  trialAccessRule: 'while stylePassMinutesLeft>0, owns(kind,id) returns true for kind IN {skin, world} for any NON-RARE item of that kind (rare skins excluded since main #8) — see load() lines 133-134 and owns() lines 257-263',
};

// --- upgrades (catalog.js UPGRADES, progress.js buyUpgrade/upgradePrice) ---
out.upgrades = {
  UPGRADE_MAX,
  table: UPGRADES.map((u) => ({ id: u.id, icon: u.icon, name: u.name, text: u.text, prices: u.prices })),
  priceFormula: 'upgradePrice(id) = prices[currentLevel] if currentLevel < UPGRADE_MAX else null (maxed); level starts at 0',
  buyFormula: 'buyUpgrade(id): if price===null or coins<price -> false; else coins-=price, upgrades[id]++, save(), true',
};

// --- run-end stat aggregation (lines 36-37, 341-342) ------------------------
out.runStatAggregation = {
  RUN_MAX_STATS: { bestScore: 'score', bestZone: 'zone', bestChain: 'bestChain', bestPowerups: 'powerups' },
  RUN_SUM_STATS: { nearTotal: 'near', plantsTotal: 'plants', coinsTotal: 'coins' },
  rule: 'for each [stat,runKey] in RUN_MAX_STATS: stats[stat] = max(stats[stat]||0, run[runKey]||0). For RUN_SUM_STATS: stats[stat] = (stats[stat]||0) + (run[runKey]||0). Both happen every finishRun() call, in that order, BEFORE achievement checks.',
  unlocksStatFormula: 'statValue("unlocks") = sum over KINDS of (items[kind].length - 1) [i.e. owned items excluding the always-free first slot] + sum of all upgrade levels',
  runsStatFormula: 'statValue("runs") = data.runs (lifetime run counter, incremented once per finishRun call)',
};

// --- achievements table (lines 12-35) ---------------------------------------
const ACHIEVEMENTS_STATS = [
  ['score10', 'bestScore', 10, 30], ['score25', 'bestScore', 25, 60], ['score50', 'bestScore', 50, 120], ['score100', 'bestScore', 100, 300],
  ['zone4', 'bestZone', 3, 150], ['near10', 'nearTotal', 10, 40], ['chain5', 'bestChain', 5, 150], ['powers3', 'bestPowerups', 3, 60],
  ['plants25', 'plantsTotal', 25, 80], ['coins500', 'coinsTotal', 500, 80], ['coins2000', 'coinsTotal', 2000, 200], ['runs50', 'runs', 50, 100],
  ['streak7', 'bestStreak', 7, 200], ['unlock5', 'unlocks', 5, 100],
  ['rareToadstool', 'plantsTotal', 150, 100], ['rareBasketball', 'runs', 200, 100], ['rareFootball', 'nearTotal', 100, 100],
  ['rareWater', 'bestPowerups', 5, 100], ['rareLava', 'coinsTotal', 10000, 100], ['rareDiamond', 'bestChain', 10, 100], ['rareGalaxy', 'bestScore', 200, 100],
];
out.achievements = {
  count: ACHIEVEMENTS_STATS.length,
  table: ACHIEVEMENTS_STATS.map(([id, stat, goal, reward]) => ({ id, stat, goal, reward })),
  rareSkinLinks: { rareToadstool: 'toadstool', rareBasketball: 'basketball', rareFootball: 'football', rareWater: 'water', rareLava: 'lava', rareDiamond: 'diamond', rareGalaxy: 'galaxy' },
  rule: 'unlockAchievements(): for each achievement not yet in data.achieved whose statValue(stat) >= goal: push to achieved, coins += reward, if a.skin present and not owned, add it to items.skin. Order = declaration order in ACHIEVEMENTS array (bestScore-based ones first, evaluated array-in-order every call, no priority beyond that).',
  wouldUnlockFormula: 'wouldUnlock(run) previews without mutating: v = statValue(stat); if stat is a RUN_MAX_STATS key, v = max(v, run[runKey]||0); if stat is a RUN_SUM_STATS key, v += run[runKey]||0 (NOTE: a stat can only be in at most one of the two maps in practice); hit if v>=goal AND not already achieved.',
};

writeFileSync(new URL('../../docs/native/golden/meta-progress-formulas.json', import.meta.url), JSON.stringify(out, null, 2));
console.log('wrote meta-progress-formulas.json');
