import { Capacitor, registerPlugin } from '@capacitor/core';
import { App } from '@capacitor/app';
import { CATALOG } from './catalog.js';
import { progress } from './progress.js';

const plugin = Capacitor.isNativePlatform() ? registerPlugin('BirdyBilling') : null;
const coinIds = ['birdy_coins_500', 'birdy_coins_1500'];
const itemIds = ['skin', 'world'].flatMap((kind) =>
  CATALOG[kind].filter((item) => item.price > 0).map((item) => `birdy_${kind}_${item.id}`));
const ids = [...coinIds, ...itemIds];
const isPermanent = (id) => itemIds.includes(id);

export const billing = {
  products: new Map(),
  ready: false,
  onChange: () => {},
  onGrant: () => {},
  get supported() { return Boolean(plugin); },
  itemId: (kind, id) => `birdy_${kind}_${id}`,
  price(id) { return this.products.get(id)?.price ?? null; },

  async init(onChange, onGrant) {
    if (!plugin) return;
    this.onChange = onChange;
    this.onGrant = onGrant;
    try {
      await plugin.addListener('purchase', (purchase) => this.handlePurchase(purchase));
      await plugin.addListener('ready', () => this.refresh());
      await App.addListener('resume', () => this.refresh());
      if ((await plugin.getStatus()).ready) await this.refresh();
    } catch { /* Play Store missing or billing temporarily unavailable */ }
  },

  async handlePurchase(purchase) {
    // Google Play reports PENDING purchases too. Entitlements start only
    // after Play confirms PURCHASED (state 1).
    if (purchase.state !== 1 || !purchase.token) return;
    const products = Array.isArray(purchase.products) ? purchase.products : [];
    for (const id of products) {
      if (coinIds.includes(id)) {
        const credited = progress.grantPurchasedCoins(id, purchase.token);
        if (credited) this.onGrant({ id, coins: credited });
        // A failed consume leaves the purchase in Play's owned list. Restore
        // retries it; the saved token prevents paying the coins twice.
        if (credited || progress.hasProcessedPurchase(purchase.token)) {
          try { await plugin.consume({ token: purchase.token }); } catch { /* retry on restore */ }
        }
      } else if (isPermanent(id)) {
        const newlyOwned = progress.grantPaidProduct(id);
        if (newlyOwned === null) continue; // never acknowledge before saving
        if (newlyOwned) this.onGrant({ id, permanent: true });
        if (!purchase.acknowledged) {
          try { await plugin.acknowledge({ token: purchase.token }); } catch { /* retry on restore */ }
        }
      }
    }
    this.onChange();
  },

  async refresh() {
    if (!plugin) return;
    if (this.refreshing) return this.refreshing;
    this.refreshing = (async () => {
      try {
        const { items } = await plugin.restore();
        const purchases = Array.isArray(items) ? items : [];
        const paid = purchases.filter((purchase) => purchase.state === 1)
          .flatMap((purchase) => purchase.products || []).filter(isPermanent);
        if (!progress.syncPaidProducts(paid)) return;
        for (const purchase of purchases) await this.handlePurchase(purchase);
        const result = await plugin.getProducts({ ids });
        this.products = new Map((result.items || []).map((product) => [product.id, product]));
        this.ready = true;
        this.onChange();
      } catch {
        // Keep previously restored purchases usable while offline.
        this.ready = false;
        this.onChange();
      }
    })();
    try { await this.refreshing; } finally { this.refreshing = null; }
  },

  async purchase(id) {
    if (!plugin || !this.products.has(id) || !ids.includes(id)) throw new Error('Product unavailable');
    // Refresh the offer immediately before starting Play's checkout.
    const result = await plugin.getProducts({ ids: [id] });
    if (!(result.items || []).some((item) => item.id === id)) throw new Error('Product unavailable');
    await plugin.purchase({ id });
  },
};
