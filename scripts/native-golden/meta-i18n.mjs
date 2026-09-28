// Golden fixture: every DE/EN string from src/i18n.js, extracted by actually
// calling the real t()/setLang()/getLang() functions (not by copy-pasting
// the STRINGS object, which isn't exported) under mocked
// localStorage/navigator/document globals. Also verifies t()'s
// {placeholder} substitution and the de-fallback-on-missing-key rule.
// Run: node scripts/native-golden/meta-i18n.mjs
// Writes: docs/native/golden/meta-i18n.json
import { writeFileSync } from 'node:fs';

// icons.js (imported by i18n.js) has no side effects needing globals, but
// i18n.js itself calls localStorage.getItem/navigator.language/document at
// module load and in applyI18n(); stub the minimum needed.
global.localStorage = (() => {
  const m = new Map();
  return { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)) };
})();
// Node 22+ already defines a read-only global `navigator`; override with
// defineProperty instead of a plain assignment.
Object.defineProperty(global, 'navigator', { value: { language: 'en-US' }, configurable: true });
global.document = { documentElement: { lang: '' }, querySelectorAll: () => [] };

const { t, L, getLang, setLang } = await import('../../src/i18n.js');

// Every key that appears in STRINGS.de (source of truth; en has the same
// key set — see i18n.js lines 5-183). Keys listed in declaration order.
const KEYS = [
  'zoneFlap', 'zoneMove', 'handFlap', 'handSide', 'best', 'howto', 'keys', 'legend', 'play', 'shop', 'achievements',
  'privacy', 'adPrivacy', 'rewardAd', 'rewardGranted', 'rewardUnavailable', 'stylePassAd', 'stylePassActive',
  'stylePassGranted', 'realBuy', 'coinPack', 'purchaseGranted', 'coinsPurchased', 'purchaseUnavailable', 'achTitle',
  'back', 'tab_skin', 'tab_pattern', 'tab_hat', 'tab_eyes', 'tab_beak', 'tab_trail', 'tab_world', 'tab_pipe',
  'tab_upgrade', 'tab_dice', 'kind_skin', 'kind_pattern', 'kind_hat', 'kind_eyes', 'kind_beak', 'kind_trail',
  'kind_world', 'kind_pipe', 'level', 'upgrade', 'maxed', 'needMore', 'rare', 'rareOr', 'rareOwned', 'skinReward',
  'surprise', 'surpriseGot', 'swipeHint', 'pause', 'resume', 'continue', 'zoneLabel', 'gameOver', 'score', 'coins',
  'newBest', 'again', 'menu', 'rotate', 'missions', 'gift', 'streak', 'buy', 'selected', 'select', 'noTrail',
  'tieRecord', 'toRecord', 'achUnlocked', 'zoneReached', 'unlockReady', 'unlockNext', 'recordToast', 'near', 'zone',
  'tutDone', 'muteOn', 'muteOff', 'lang', 'langLabel',
];

const out = { source: 'src/i18n.js', keyCount: KEYS.length };

setLang('de');
out.de = Object.fromEntries(KEYS.map((k) => [k, t(k)]));
setLang('en');
out.en = Object.fromEntries(KEYS.map((k) => [k, t(k)]));

// Icon-tag convention: strings may embed [iconName] which icons.js's rich()
// turns into an inline <span class="ic"><svg badge>...</span>. List which
// keys use it and which icon names.
const iconTagRe = /\[(\w+)\]/g;
out.keysWithIconTags = {};
for (const lang of ['de', 'en']) {
  for (const k of KEYS) {
    const s = out[lang][k];
    const tags = [...s.matchAll(iconTagRe)].map((m) => m[1]);
    if (tags.length) (out.keysWithIconTags[k] ??= {})[lang] = tags;
  }
}

// {placeholder} substitution semantics: t(key, values) does
// s.replace(/\{(\w+)\}/g, (_, k) => values[k] ?? ''); missing values become
// the empty string, not left as literal "{n}".
setLang('en');
out.placeholderSubstitution = {
  formula: 's.replace(/\\{(\\w+)\\}/g, (_, k) => values[k] ?? \'\')',
  example_rewardAd_n3: t('rewardAd', { n: 3 }),
  example_missingValue_becomesEmptyString: t('rewardAd', {}),
  example_multiPlaceholder_streak: t('streak', { d: 5, n: 60 }),
};

// getLang()/setLang() behaviour: unknown language is a silent no-op.
setLang('fr');
out.setLangUnknownIsNoOp = { afterSetLangFr: getLang() }; // stays 'en' from the previous setLang('en')
setLang('de');
out.setLangDe = { getLang: getLang() };

// t() fallback rule: unknown lang string falls back to STRINGS.de[key], and
// an unknown key falls back to returning the key itself (line 207).
out.fallbackRule = {
  formula: 'STRINGS[lang][key] ?? STRINGS.de[key] ?? key',
  unknownKeyReturnsKeyItself: t('___doesNotExist___'),
};

// L(): localizes a {de,en} object OR passes through a plain string.
out.L_helper = {
  formula: 'L(v) = (v && typeof v === "object") ? (v[lang] ?? v.de) : v',
  example_object_de: (() => { setLang('de'); return L({ de: 'Hallo', en: 'Hello' }); })(),
  example_object_en: (() => { setLang('en'); return L({ de: 'Hallo', en: 'Hello' }); })(),
  example_plainString: L('unchanged'),
  example_missingLangFallsBackToDe: (() => { setLang('en'); return L({ de: 'NurDeutsch' }); })(),
};

// Language auto-detection rule (detect(), lines 185-192): localStorage
// 'birdy-lang' wins if set to a known language; else navigator.language
// prefix 'de' -> 'de', anything else -> 'en'. Cannot re-exercise detect()
// itself post-import (it only runs once at module load into the `lang`
// closure var), so it's documented here as a formula for the port instead.
out.detectFormula = {
  rule1: 'if localStorage.getItem("birdy-lang") is "de" or "en", use it',
  rule2: 'else: navigator.language.toLowerCase().startsWith("de") ? "de" : "en"',
  storageKey: 'birdy-lang',
};

writeFileSync(new URL('../../docs/native/golden/meta-i18n.json', import.meta.url), JSON.stringify(out, null, 2));
console.log('wrote meta-i18n.json');
