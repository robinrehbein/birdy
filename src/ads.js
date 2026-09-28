import { Capacitor, registerPlugin } from '@capacitor/core';

const native = Capacitor.isNativePlatform();
const plugin = native ? registerPlugin('BirdyAds') : null;

export const ads = {
  available: false,
  passAvailable: false,
  privacyOptionsRequired: false,

  async init(onChange) {
    if (!plugin) return;
    const update = (status) => {
      this.available = Boolean(status.ready);
      this.passAvailable = Boolean(status.passReady);
      this.privacyOptionsRequired = Boolean(status.privacyOptionsRequired);
      onChange();
    };
    try {
      await plugin.addListener('status', update);
      update(await plugin.getStatus());
    } catch { /* no Google services or offline */ }
  },

  async showRewarded(kind = 'coins') {
    if (!plugin || !(kind === 'pass' ? this.passAvailable : this.available)) return false;
    if (kind === 'pass') this.passAvailable = false;
    else this.available = false;
    try {
      return Boolean((await plugin.showRewarded({ kind })).earned);
    } catch {
      return false;
    } finally {
      try {
        const status = await plugin.getStatus();
        this.available = Boolean(status.ready);
        this.passAvailable = Boolean(status.passReady);
        this.privacyOptionsRequired = Boolean(status.privacyOptionsRequired);
      } catch { /* remain unavailable */ }
    }
  },

  async showPrivacyOptions() {
    if (plugin && this.privacyOptionsRequired) await plugin.showPrivacyOptions();
  },
};
