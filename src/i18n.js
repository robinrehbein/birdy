// Tiny i18n: German and English, picked from the device language (switchable
// in the menu). Strings with {placeholders} are filled by t(key, values).
const STRINGS = {
  de: {
    zoneFlap: 'flattern',
    zoneMove: 'hierhin',
    handFlap: 'Tippen = flattern',
    handSide: 'Andere Bahn tippen<br>= ausweichen',
    best: 'Rekord',
    howto: 'Tippe auf <b>die Bahn des Vogels</b> zum Flattern,<br />auf <b>eine andere Bahn</b> zum Wechseln.',
    keys: 'Tastatur: Leertaste / ↑ flattern, ← → Spur',
    legend: '🌈 unverwundbar · 🧲 Münz-Magnet · 🍄 Mini-Vogel',
    play: "Los geht's",
    shop: '🐦 Shop',
    achievements: '🏆 Erfolge',
    achTitle: 'Erfolge',
    back: 'Zurück',
    tabSkins: '🐦 Vögel',
    tabTrails: '✨ Spuren',
    pause: 'Pause',
    resume: 'Tippen zum Weiterspielen',
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
    kindBird: 'Vogel',
    kindTrail: 'Spur',
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
    howto: "Tap <b>the bird's lane</b> to flap,<br />tap <b>another lane</b> to switch.",
    keys: 'Keyboard: Space / ↑ flap, ← → lane',
    legend: '🌈 invincible · 🧲 coin magnet · 🍄 mini bird',
    play: 'Play',
    shop: '🐦 Shop',
    achievements: '🏆 Awards',
    achTitle: 'Awards',
    back: 'Back',
    tabSkins: '🐦 Birds',
    tabTrails: '✨ Trails',
    pause: 'Paused',
    resume: 'Tap to continue',
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
    kindBird: 'bird',
    kindTrail: 'trail',
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
