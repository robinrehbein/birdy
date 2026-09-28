// Tiny i18n: German and English, picked from the device language (switchable
// in the menu). Strings with {placeholders} are filled by t(key, values).
const STRINGS = {
  de: {
    zoneFlap: 'flattern',
    zoneMove: 'hierhin',
    handFlap: 'Tippen = flattern',
    handSide: 'Andere Bahn tippen<br>= ausweichen',
    best: 'Rekord',
    howto: 'Tippe auf <b>die Bahn des Vogels</b> zum Flattern,<br />auf <b>eine andere Bahn</b> zum Wechseln – oder wische.',
    keys: 'Tastatur: Leertaste / ↑ flattern, ← → Spur',
    legend: '🌈 unverwundbar · 🧲 Münz-Magnet · 🍄 Mini-Vogel',
    play: "Los geht's",
    shop: '🐦 Shop',
    achievements: '🏆 Erfolge',
    privacy: 'Datenschutz',
    adPrivacy: 'Werbe-Datenschutz',
    rewardAd: '▶ Anzeige ansehen · +30 Münzen ({n}/3 heute)',
    rewardGranted: '+30 Münzen für die Anzeige!',
    rewardUnavailable: 'Gerade keine Anzeige verfügbar',
    stylePassAd: '▶ 1 Stunde alle Vögel & Welten testen · Anzeige ({n}/3 heute)',
    stylePassActive: '✨ Style-Pass aktiv · noch {n} Min.',
    stylePassGranted: 'Alle Vögel & Welten für 1 Stunde freigeschaltet!',
    realBuy: 'Dauerhaft kaufen · {price} Echtgeld',
    coinPack: '{n} Münzen · {price} Echtgeld',
    purchaseGranted: 'Dauerhaft freigeschaltet!',
    coinsPurchased: '+{n} Münzen gekauft!',
    purchaseUnavailable: 'Kauf gerade nicht möglich',
    achTitle: 'Erfolge',
    back: 'Zurück',
    tab_skin: 'Farbe',
    tab_pattern: 'Muster',
    tab_hat: 'Kopf',
    tab_eyes: 'Augen',
    tab_beak: 'Schnabel',
    tab_trail: 'Spuren',
    tab_world: 'Welten',
    tab_pipe: 'Röhren',
    tab_upgrade: 'Power',
    tab_dice: 'Zufall',
    kind_skin: 'Farbe',
    kind_pattern: 'Muster',
    kind_hat: 'Kopfschmuck',
    kind_eyes: 'Augen',
    kind_beak: 'Schnabel',
    kind_trail: 'Spur',
    kind_world: 'Welt',
    kind_pipe: 'Röhren-Design',
    level: 'Stufe {n}/{max}',
    upgrade: 'Verbessern · {n}',
    maxed: 'Maximal',
    needMore: 'Noch {n}',
    surprise: '🎁 Überraschung · {n}',
    surpriseGot: '🎁 Neu: {name}!',
    swipeHint: '👆 Tipp: Du kannst auch nach links/rechts wischen',
    pause: 'Pause',
    resume: 'Tippen zum Weiterspielen',
    continue: 'Weiter',
    zoneLabel: 'Zone',
    gameOver: 'Game Over',
    score: 'Punkte',
    coins: 'Münzen',
    newBest: 'Neuer Rekord!',
    again: 'Nochmal',
    menu: 'Menü',
    rotate: 'Bitte Handy hochkant halten',
    missions: 'Tagesmissionen',
    gift: '🎁 Tagesgeschenk · +{n}',
    streak: '🔥 Serie: Tag {d} · morgen +{n}',
    buy: 'Kaufen · {n}',
    selected: 'Ausgewählt',
    select: 'Auswählen',
    noTrail: 'Keine Spur',
    tieRecord: 'Rekord eingestellt!',
    toRecord: 'Nur noch {n} bis zum Rekord!',
    achUnlocked: 'Erfolg: {name}',
    zoneReached: 'Zone {n} erreicht: {name}',
    unlockReady: '✨ {kind} „{name}“ jetzt freischaltbar! ›',
    unlockNext: 'Noch {n} 🪙 bis {kind} „{name}“',
    recordToast: '🏆 Neuer Rekord!',
    near: 'Knapp!',
    zone: 'Zone {n}',
    tutDone: 'Super! Jetzt allein weiter 🎉',
    muteOn: 'Ton an',
    muteOff: 'Ton aus',
    lang: 'EN',
    langLabel: 'Switch to English',
  },
  en: {
    zoneFlap: 'flap',
    zoneMove: 'move here',
    handFlap: 'Tap = flap',
    handSide: 'Tap another lane<br>= dodge',
    best: 'Best',
    howto: "Tap <b>the bird's lane</b> to flap,<br />tap <b>another lane</b> or swipe to switch.",
    keys: 'Keyboard: Space / ↑ flap, ← → lane',
    legend: '🌈 invincible · 🧲 coin magnet · 🍄 mini bird',
    play: 'Play',
    shop: '🐦 Shop',
    achievements: '🏆 Awards',
    privacy: 'Privacy policy',
    adPrivacy: 'Ad privacy choices',
    rewardAd: '▶ Watch an ad · +30 coins ({n}/3 today)',
    rewardGranted: '+30 coins for the ad!',
    rewardUnavailable: 'No ad available right now',
    stylePassAd: '▶ Try all birds & worlds for 1 hour · ad ({n}/3 today)',
    stylePassActive: '✨ Style Pass active · {n} min left',
    stylePassGranted: 'All birds & worlds unlocked for 1 hour!',
    realBuy: 'Own forever · {price} real money',
    coinPack: '{n} coins · {price} real money',
    purchaseGranted: 'Unlocked permanently!',
    coinsPurchased: '+{n} coins purchased!',
    purchaseUnavailable: 'Purchase unavailable right now',
    achTitle: 'Awards',
    back: 'Back',
    tab_skin: 'Colour',
    tab_pattern: 'Pattern',
    tab_hat: 'Hats',
    tab_eyes: 'Eyes',
    tab_beak: 'Beak',
    tab_trail: 'Trails',
    tab_world: 'Worlds',
    tab_pipe: 'Pipes',
    tab_upgrade: 'Power',
    tab_dice: 'Random',
    kind_skin: 'colour',
    kind_pattern: 'pattern',
    kind_hat: 'hat',
    kind_eyes: 'eyes',
    kind_beak: 'beak',
    kind_trail: 'trail',
    kind_world: 'world',
    kind_pipe: 'pipe design',
    level: 'level {n}/{max}',
    upgrade: 'Upgrade · {n}',
    maxed: 'Maxed out',
    needMore: '{n} more',
    surprise: '🎁 Surprise · {n}',
    surpriseGot: '🎁 New: {name}!',
    swipeHint: '👆 Tip: you can also swipe left/right',
    pause: 'Paused',
    resume: 'Tap to continue',
    continue: 'Continue',
    zoneLabel: 'Zone',
    gameOver: 'Game Over',
    score: 'Score',
    coins: 'Coins',
    newBest: 'New best!',
    again: 'Again',
    menu: 'Menu',
    rotate: 'Please hold your phone upright',
    missions: 'Daily missions',
    gift: '🎁 Daily gift · +{n}',
    streak: '🔥 Streak: day {d} · tomorrow +{n}',
    buy: 'Buy · {n}',
    selected: 'Selected',
    select: 'Select',
    noTrail: 'No trail',
    tieRecord: 'You tied your best!',
    toRecord: 'Only {n} more to beat your best!',
    achUnlocked: 'Award: {name}',
    zoneReached: 'Reached zone {n}: {name}',
    unlockReady: '✨ {kind} “{name}” ready to unlock! ›',
    unlockNext: '{n} 🪙 more for {kind} “{name}”',
    recordToast: '🏆 New best!',
    near: 'Close call!',
    zone: 'Zone {n}',
    tutDone: 'Great! Now on your own 🎉',
    muteOn: 'Sound on',
    muteOff: 'Sound off',
    lang: 'DE',
    langLabel: 'Auf Deutsch umstellen',
  },
};

function detect() {
  try {
    const saved = localStorage.getItem('birdy-lang');
    if (saved && STRINGS[saved]) return saved;
  } catch { /* ignore */ }
  const nav = (navigator.language || 'en').toLowerCase();
  return nav.startsWith('de') ? 'de' : 'en';
}

let lang = detect();

export function getLang() {
  return lang;
}

export function setLang(l) {
  if (!STRINGS[l]) return;
  lang = l;
  try { localStorage.setItem('birdy-lang', l); } catch { /* ignore */ }
}

export function t(key, values) {
  let s = STRINGS[lang][key] ?? STRINGS.de[key] ?? key;
  if (values) s = s.replace(/\{(\w+)\}/g, (_, k) => values[k] ?? '');
  return s;
}

// Localise a value that is either a plain string or { de, en }.
export function L(v) {
  if (v && typeof v === 'object') return v[lang] ?? v.de;
  return v;
}

// Fill every element with data-i18n (text) / data-i18n-html (markup).
export function applyI18n(root = document) {
  document.documentElement.lang = lang;
  for (const el of root.querySelectorAll('[data-i18n]')) el.textContent = t(el.dataset.i18n);
  for (const el of root.querySelectorAll('[data-i18n-html]')) el.innerHTML = t(el.dataset.i18nHtml);
}
