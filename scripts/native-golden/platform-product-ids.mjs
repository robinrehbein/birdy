// Golden fixture for the Play Billing product-id universe: reproduces the
// pure, catalog-derived logic in src/billing.js:7-11 (coinIds/itemIds/ids/
// isPermanent) so a Kotlin port can hardcode or derive the exact same set.
// Imports the real catalog (pure data, no DOM/THREE side effects).
// Run: node scripts/native-golden/platform-product-ids.mjs
import { writeFileSync } from 'node:fs';
import { CATALOG } from '../../src/catalog.js';

const coinIds = ['birdy_coins_500', 'birdy_coins_1500'];
const itemIds = ['skin', 'world'].flatMap((kind) =>
  CATALOG[kind].filter((item) => item.price > 0 && !item.rare).map((item) => `birdy_${kind}_${item.id}`));
const ids = [...coinIds, ...itemIds];

// Purchasable skins/worlds with their coin price (for cross-checking against
// the EUR prices published in docs/MONETIZATION.md — Play sets the actual
// EUR price server-side, coin price is the in-game alternative).
const purchasable = ['skin', 'world'].flatMap((kind) =>
  CATALOG[kind].filter((item) => item.price > 0 && !item.rare)
    .map((item) => ({ id: `birdy_${kind}_${item.id}`, kind, itemId: item.id, coinPrice: item.price })));

const out = {
  coinIds,
  itemIds,
  ids,
  count: ids.length,
  purchasable,
};
writeFileSync(new URL('../../docs/native/golden/platform-product-ids.json', import.meta.url), JSON.stringify(out, null, 2) + '\n');
console.log(`Wrote ${ids.length} product ids (${coinIds.length} coin packs, ${itemIds.length} permanent items).`);
