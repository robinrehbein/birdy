// Tiny i18n: German and English, picked from the device language (overridable
// in the menu). Strings with {placeholders} are filled by t(key, values).
const STRINGS = {
  de: {
    zoneFlap: 'flattern',
    zoneMove: 'hierhin',
    handFlap: 'Tippen = flattern',
    handSide: 'Andere Bahn tippen<br>= ausweichen',
  },
  en: {
    zoneFlap: 'flap',
    zoneMove: 'move here',
    handFlap: 'Tap = flap',
    handSide: 'Tap another lane<br>= dodge',
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

export { STRINGS };
