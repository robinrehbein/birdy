import assert from 'node:assert/strict';

const stored = new Map();
globalThis.localStorage = {
  getItem: (key) => stored.get(key) ?? null,
  setItem: (key, value) => stored.set(key, value),
};

let now = Date.now();
Date.now = () => now;
const reload = async (name) => (await import(`../src/progress.js?${name}`)).progress;

let progress = await reload('initial');
assert.equal(progress.permanentlyOwns('skin', 'gold'), false);
assert.equal(progress.grantPurchasedCoins('birdy_coins_500', 'purchase-A'), 500);
assert.equal(progress.grantPurchasedCoins('birdy_coins_500', 'purchase-A'), 0);
assert.equal(progress.coins, 500);
assert.equal(progress.grantPaidProduct('birdy_skin_gold'), true);
assert.equal(progress.permanentlyOwns('skin', 'gold'), true);

progress = await reload('restart');
assert.equal(progress.coins, 500);
assert.equal(progress.permanentlyOwns('skin', 'gold'), true);
assert.equal(progress.grantPurchasedCoins('birdy_coins_500', 'purchase-A'), 0);
assert.equal(progress.grantPurchasedCoins('birdy_coins_1500', 'purchase-B'), 1500);
assert.equal(progress.coins, 2000);
assert.equal(progress.grantStylePass(), true);
assert.equal(progress.owns('world', 'mushroom'), true);

now += 61 * 60_000;
progress = await reload('expired');
assert.equal(progress.owns('world', 'mushroom'), false);
assert.equal(progress.permanentlyOwns('skin', 'gold'), true);
assert.equal(progress.syncPaidProducts([]), true);
assert.equal(progress.permanentlyOwns('skin', 'gold'), false);
assert.equal(progress.coins, 2000);

console.log('Monetization state checks passed');
