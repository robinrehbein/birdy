// Everything the shop sells. Each category ("kind") is a list of items with
// an id, a { de, en } name and a price; the first item of a kind is free and
// owned from the start. Cosmetics only change looks; upgrades (see
// UPGRADES) make the power-ups a little stronger.

// Colour sets for the bird. Same model, same low-poly look.
export const SKINS = [
  { id: 'sunny', name: { de: 'Sunny', en: 'Sunny' }, price: 0, body: 0xf7d23e, belly: 0xfff3c4, wing: 0xfff6d5, cover: 0xf6e3a1, tail: 0xf2c230, beak: 0xf57c21, beakLow: 0xe0521b },
  { id: 'sky', name: { de: 'Himmel', en: 'Sky' }, price: 100, body: 0x4aa8f0, belly: 0xe8f6ff, wing: 0xdff1ff, cover: 0x9fd2fa, tail: 0x2f86d0, beak: 0xf5a623, beakLow: 0xe07b1b },
  { id: 'cardinal', name: { de: 'Kardinal', en: 'Cardinal' }, price: 250, body: 0xe8453c, belly: 0xffd7c9, wing: 0xffe3dc, cover: 0xf28b82, tail: 0xc4302b, beak: 0xffc93c, beakLow: 0xf0a020 },
  { id: 'robin', name: { de: 'Rotkehlchen', en: 'Robin' }, price: 300, body: 0x9a6b4a, belly: 0xff8a3d, wing: 0xe9d6c4, cover: 0xb88a66, tail: 0x7d5238, beak: 0x4a3b33, beakLow: 0x33271f },
  { id: 'mint', name: { de: 'Minze', en: 'Mint' }, price: 400, body: 0x5fd39a, belly: 0xeafff3, wing: 0xe3fff0, cover: 0xa6ecc8, tail: 0x3bb37b, beak: 0xff8a5c, beakLow: 0xe8643a },
  { id: 'coral', name: { de: 'Koralle', en: 'Coral' }, price: 500, body: 0xff7f6b, belly: 0xfff0e6, wing: 0xffe5dc, cover: 0xffb3a3, tail: 0xe8604e, beak: 0x5ad1ff, beakLow: 0x2fa8d9 },
  { id: 'flamingo', name: { de: 'Flamingo', en: 'Flamingo' }, price: 600, body: 0xff8fb8, belly: 0xffe6f0, wing: 0xfff0f6, cover: 0xffc2d8, tail: 0xf2649a, beak: 0x4a3b47, beakLow: 0x2f2530 },
  { id: 'parrot', name: { de: 'Papagei', en: 'Parrot' }, price: 750, body: 0x3cc45a, belly: 0xffe14a, wing: 0xff5a4a, cover: 0x4ab8ff, tail: 0x2f86d0, beak: 0xf2eee0, beakLow: 0x3a3a3a },
  { id: 'penguin', name: { de: 'Pinguin', en: 'Penguin' }, price: 900, body: 0x3a4250, belly: 0xffffff, wing: 0x505a6c, cover: 0x2e3440, tail: 0x2e3440, beak: 0xffa31a, beakLow: 0xf07b12 },
  { id: 'night', name: { de: 'Nachteule', en: 'Night Owl' }, price: 1000, body: 0x5b4b8a, belly: 0xd9d0f5, wing: 0xc7bdf0, cover: 0x8f80c9, tail: 0x44376e, beak: 0xffc93c, beakLow: 0xe8a820 },
  { id: 'snowy', name: { de: 'Schneeeule', en: 'Snowy Owl' }, price: 1200, body: 0xf4f7fb, belly: 0xffffff, wing: 0xdde6f0, cover: 0xc9d6e3, tail: 0xb8c6d6, beak: 0x4a4a4a, beakLow: 0x2e2e2e },
  { id: 'peacock', name: { de: 'Pfau', en: 'Peacock' }, price: 1400, body: 0x1f8fb0, belly: 0x7be0c8, wing: 0x3ccfa0, cover: 0x2a6fd0, tail: 0x1ea06a, beak: 0xffd24a, beakLow: 0xe8a820 },
  { id: 'gold', name: { de: 'Goldvogel', en: 'Golden Bird' }, price: 2000, body: 0xffc629, belly: 0xfff1b0, wing: 0xffe57a, cover: 0xffd23d, tail: 0xe0a100, beak: 0xff7a1a, beakLow: 0xd9530f, metal: true },
  // Rare animated skins (skinfx.js): expensive, or free with their own hard
  // achievement (progress.js, `skin` there). `swatch` is the shop tile.
  { id: 'toadstool', fx: 'toadstool', rare: true, name: { de: 'Fliegenpilz', en: 'Toadstool' }, price: 3000, body: 0xe0302a, belly: 0xfff3e0, wing: 0xe0302a, cover: 0xc4241f, tail: 0xc4241f, beak: 0xfff3e0, beakLow: 0xe8d8c0,
    swatch: 'radial-gradient(circle at 30% 35%, #fff 0 9%, transparent 10%), radial-gradient(circle at 68% 62%, #fff 0 12%, transparent 13%), radial-gradient(circle at 70% 25%, #fff 0 6%, transparent 7%), #e0302a' },
  { id: 'basketball', fx: 'basketball', rare: true, name: { de: 'Basketball', en: 'Basketball' }, price: 3500, body: 0xf26b1d, belly: 0xffa860, wing: 0xf26b1d, cover: 0xd9530f, tail: 0xd9530f, beak: 0x3a2418, beakLow: 0x2a1a10,
    swatch: 'linear-gradient(90deg, transparent 47%, #3a2418 47% 53%, transparent 53%), linear-gradient(transparent 47%, #3a2418 47% 53%, transparent 53%), radial-gradient(circle at 35% 30%, #ffa860, #f26b1d 60%)' },
  { id: 'football', fx: 'football', rare: true, name: { de: 'Fußball', en: 'Football' }, price: 4000, body: 0xf4f4f4, belly: 0xffffff, wing: 0xe6e6e6, cover: 0x2a2a2a, tail: 0x2a2a2a, beak: 0xf57c21, beakLow: 0xe0521b,
    swatch: 'radial-gradient(circle at 50% 50%, #222 0 16%, transparent 17%), radial-gradient(circle at 12% 15%, #222 0 13%, transparent 14%), radial-gradient(circle at 88% 20%, #222 0 13%, transparent 14%), radial-gradient(circle at 20% 90%, #222 0 13%, transparent 14%), radial-gradient(circle at 85% 88%, #222 0 13%, transparent 14%), #f4f4f4' },
  { id: 'water', fx: 'water', rare: true, name: { de: 'Wasser', en: 'Water' }, price: 4500, body: 0x1e7fd6, belly: 0xbfe9ff, wing: 0x1e7fd6, cover: 0x3aa0f0, tail: 0x1466b0, beak: 0xffc93c, beakLow: 0xe8a820,
    swatch: 'radial-gradient(circle at 30% 70%, transparent 0 7%, #e8f7ff 8% 11%, transparent 12%), radial-gradient(circle at 65% 40%, transparent 0 5%, #e8f7ff 6% 9%, transparent 10%), repeating-linear-gradient(160deg, #1e7fd6 0 10px, #4ab8ff 10px 16px)' },
  { id: 'lava', fx: 'lava', rare: true, name: { de: 'Lava', en: 'Lava' }, price: 5000, body: 0x3a2420, belly: 0x5a3028, wing: 0x3a2420, cover: 0x2a1a18, tail: 0x2a1a18, beak: 0x2a2a2a, beakLow: 0x1a1a1a,
    swatch: 'linear-gradient(35deg, transparent 40%, #ff8a1a 40% 46%, transparent 46%), linear-gradient(-50deg, transparent 55%, #ffb03a 55% 60%, transparent 60%), linear-gradient(80deg, transparent 20%, #ff6a10 20% 25%, transparent 25%), #3a2420' },
  { id: 'diamond', fx: 'diamond', rare: true, name: { de: 'Diamant', en: 'Diamond' }, price: 5500, body: 0x9fe0ff, belly: 0xe6fbff, wing: 0x9fe0ff, cover: 0x7fd0f5, tail: 0x7fd0f5, beak: 0xbfefff, beakLow: 0x8fd3f0,
    swatch: 'conic-gradient(from 20deg, #e6fbff, #8fd3ff, #ffffff, #b8a8ff, #e6fbff, #7fe0f0, #ffffff, #e6fbff)' },
  { id: 'galaxy', fx: 'galaxy', rare: true, name: { de: 'Galaxie', en: 'Galaxy' }, price: 6000, body: 0x2a1a60, belly: 0x6a3a9a, wing: 0x2a1a60, cover: 0x4a2a8a, tail: 0x4a2a8a, beak: 0xffe14a, beakLow: 0xf0b820,
    swatch: 'radial-gradient(circle at 25% 30%, #fff 0 3%, transparent 4%), radial-gradient(circle at 70% 60%, #fff 0 4%, transparent 5%), radial-gradient(circle at 60% 20%, #fff 0 2%, transparent 3%), radial-gradient(circle at 30% 75%, #8f63d6, transparent 45%), radial-gradient(circle at 75% 35%, #4ab8ff, transparent 40%), #1a1040' },
];

// Patterns painted onto the body (small extra shapes in the skin's colours).
export const PATTERNS = [
  { id: 'plain', icon: '○', name: { de: 'Schlicht', en: 'Plain' }, price: 0 },
  { id: 'cheeks', icon: '😊', name: { de: 'Bäckchen', en: 'Rosy Cheeks' }, price: 150 },
  { id: 'spots', icon: '⚬', name: { de: 'Tupfen', en: 'Spots' }, price: 300 },
  { id: 'stripes', icon: '≡', name: { de: 'Streifen', en: 'Stripes' }, price: 450 },
  { id: 'mask', icon: '🦝', name: { de: 'Räubermaske', en: 'Bandit Mask' }, price: 600 },
  { id: 'heart', icon: '💗', name: { de: 'Herz am Rücken', en: 'Back Heart' }, price: 800 },
];

// Headwear.
export const HATS = [
  { id: 'none', icon: '✕', name: { de: 'Ohne', en: 'None' }, price: 0 },
  { id: 'crest', icon: '🪶', name: { de: 'Federschopf', en: 'Crest' }, price: 200 },
  { id: 'flower', icon: '🌼', name: { de: 'Blume', en: 'Flower' }, price: 300 },
  { id: 'party', icon: '🎉', name: { de: 'Partyhut', en: 'Party Hat' }, price: 450 },
  { id: 'cap', icon: '🧢', name: { de: 'Propellermütze', en: 'Propeller Cap' }, price: 650 },
  { id: 'tophat', icon: '🎩', name: { de: 'Zylinder', en: 'Top Hat' }, price: 850 },
  { id: 'viking', icon: '⚔️', name: { de: 'Wikingerhelm', en: 'Viking Helmet' }, price: 1100 },
  { id: 'crown', icon: '👑', name: { de: 'Krone', en: 'Crown' }, price: 1500 },
  { id: 'halo', icon: '😇', name: { de: 'Heiligenschein', en: 'Halo' }, price: 1800 },
];

// Eyes and eyewear.
export const EYES = [
  { id: 'normal', icon: '👀', name: { de: 'Kulleraugen', en: 'Big Eyes' }, price: 0 },
  { id: 'lashes', icon: '✨', name: { de: 'Wimpern', en: 'Lashes' }, price: 150 },
  { id: 'brows', icon: '😠', name: { de: 'Entschlossen', en: 'Determined' }, price: 250 },
  { id: 'shades', icon: '🕶️', name: { de: 'Sonnenbrille', en: 'Shades' }, price: 500 },
  { id: 'hearts', icon: '😍', name: { de: 'Herzbrille', en: 'Heart Glasses' }, price: 700 },
  { id: 'goggles', icon: '🥽', name: { de: 'Fliegerbrille', en: 'Flight Goggles' }, price: 900 },
];

// Beak shapes. `own` colours override the skin's beak colours.
export const BEAKS = [
  { id: 'round', icon: '🐤', name: { de: 'Rundschnabel', en: 'Round Beak' }, price: 0 },
  { id: 'duck', icon: '🦆', name: { de: 'Entenschnabel', en: 'Duck Bill' }, price: 300 },
  { id: 'hook', icon: '🦅', name: { de: 'Adlerschnabel', en: 'Eagle Beak' }, price: 600 },
  { id: 'toucan', icon: '🌴', name: { de: 'Tukan', en: 'Toucan' }, price: 1000 },
];

// Flight trails: particles behind the bird.
export const TRAILS = [
  { id: 'none', name: { de: 'Keine Spur', en: 'No trail' }, price: 0, colors: [] },
  { id: 'sparkle', name: { de: 'Funkeln', en: 'Sparkle' }, price: 150, colors: [0xfff176, 0xffffff, 0xffd400], size: 0.08, life: 0.45, gravity: 0, speed: 0.8 },
  { id: 'bubbles', name: { de: 'Blasen', en: 'Bubbles' }, price: 300, colors: [0xbfe9ff, 0xe8f7ff, 0x8fd3ff], size: 0.14, life: 0.8, gravity: 2.5, speed: 0.5 },
  { id: 'hearts', name: { de: 'Herzchen', en: 'Hearts' }, price: 400, colors: [0xff5a8a, 0xff9ab8, 0xffffff], size: 0.12, life: 0.7, gravity: 1.5, speed: 0.6 },
  { id: 'confetti', name: { de: 'Konfetti', en: 'Confetti' }, price: 500, colors: [0xff5a5a, 0x5ad1ff, 0xffd84a, 0x7be07b, 0xc58bff], size: 0.09, life: 0.7, gravity: -4, speed: 2 },
  { id: 'snow', name: { de: 'Schneeflocken', en: 'Snowflakes' }, price: 600, colors: [0xffffff, 0xe3f4ff, 0xbfe3ff], size: 0.1, life: 1.1, gravity: -1.2, speed: 0.9 },
  { id: 'leaves', name: { de: 'Herbstlaub', en: 'Autumn Leaves' }, price: 700, colors: [0xe8772e, 0xf2a93b, 0xd9492f], size: 0.12, life: 0.9, gravity: -2, speed: 1.2 },
  { id: 'neon', name: { de: 'Neon', en: 'Neon' }, price: 850, colors: [0x39ff14, 0xff2fd6, 0x00e5ff], size: 0.08, life: 0.5, gravity: 0, speed: 1.4 },
  { id: 'stardust', name: { de: 'Sternenstaub', en: 'Stardust' }, price: 1000, colors: [0xc58bff, 0xffffff, 0x8f7bff], size: 0.07, life: 0.9, gravity: 0.5, speed: 0.6 },
  { id: 'rainbow', name: { de: 'Regenbogen', en: 'Rainbow' }, price: 1200, colors: [0xff5a5a, 0xffa43a, 0xffe14a, 0x6fd86a, 0x4ab8ff, 0x9a7bff], size: 0.11, life: 0.6, gravity: 0, speed: 0.3 },
  { id: 'fire', name: { de: 'Feuerschweif', en: 'Fire Tail' }, price: 1400, colors: [0xff7a1a, 0xffc93c, 0xff3d2e], size: 0.13, life: 0.4, gravity: 3, speed: 0.9 },
  { id: 'goldrain', name: { de: 'Goldregen', en: 'Gold Rain' }, price: 1800, colors: [0xffd400, 0xffe57a, 0xfff6c4], size: 0.1, life: 0.8, gravity: -5, speed: 1 },
];

// Worlds: the place of the first zone (and every fourth after it). The run
// still moves on through autumn forest, canyon and blossom grove. Colours
// for sky, light and ground as in biomes.js; `scenery` is a theme in world.js.
// `road` is the track palette [track, stripes, border] and `pipes` the pipe
// colours used while the classic pipes are equipped (owner's picks, it19).
export const WORLDS = [
  { id: 'park', icon: 'tree', name: { de: 'Stadtpark', en: 'City Park' }, price: 0, scenery: 'park',
    top: 0x2a9bd0, horizon: 0xa6e4ea, hemiSky: 0xdff6ff, hemiGround: 0x6a8f3a, hemiI: 1.4, sun: 0xfff4d6, sunI: 2.2,
    tint: 0xffffff, clouds: 0xffffff, grass: 0x73bf2e, track: 0xffffff },
  { id: 'winter', icon: 'snowflake', name: { de: 'Winterland', en: 'Winterland' }, price: 1200, scenery: 'winter',
    top: 0x5aa9e6, horizon: 0xe3f2ff, hemiSky: 0xffffff, hemiGround: 0x9fb4c8, hemiI: 1.45, sun: 0xfff6e8, sunI: 2.0,
    tint: 0xffffff, clouds: 0xffffff, grass: 0xe6f0fb, track: 0xe4ecf8,
    road: [0xeef4fb, 0xd6e4f2, 0xbfe3ff], pipes: { pipe: 0x5fc6e6, light: 0xe6fbff, dark: 0x3a93b8 } },
  { id: 'beach', icon: 'palm', name: { de: 'Südsee', en: 'South Seas' }, price: 1800, scenery: 'beach',
    top: 0x1fa5e0, horizon: 0xbff3f0, hemiSky: 0xe8fbff, hemiGround: 0xc9b27a, hemiI: 1.45, sun: 0xfff4d6, sunI: 2.3,
    tint: 0xffffff, clouds: 0xffffff, grass: 0xf2dc9b, track: 0xffffff,
    road: [0xf7e6b0, 0xefd48a, 0x5fd3d0], pipes: { pipe: 0x2fbfb3, light: 0x9ff0e6, dark: 0x1f8a80 } },
  { id: 'candy', icon: 'lollipop', name: { de: 'Zuckerland', en: 'Candyland' }, price: 2400, scenery: 'candy',
    top: 0xf07ab8, horizon: 0xffe3f1, hemiSky: 0xfff0f8, hemiGround: 0xc98fb8, hemiI: 1.45, sun: 0xfff0f6, sunI: 2.0,
    tint: 0xffffff, clouds: 0xfff0fa, grass: 0xffb3d9, track: 0xfff0f6,
    road: [0xffd1e6, 0xffb3d4, 0xffffff], pipes: { pipe: 0xff6fa8, light: 0xffffff, dark: 0xd9407c } },
  { id: 'mushroom', icon: 'mushroom', name: { de: 'Pilzwald', en: 'Mushroom Woods' }, price: 3000, scenery: 'mushroom',
    top: 0x4a6fd0, horizon: 0xc8f0d8, hemiSky: 0xeafff2, hemiGround: 0x5a8a5a, hemiI: 1.4, sun: 0xfff0c8, sunI: 2.1,
    tint: 0xffffff, clouds: 0xf0e8ff, grass: 0x6fc45a, track: 0xfffaf0,
    road: [0xd6e6a6, 0xbfd188, 0x8fe070], pipes: { pipe: 0xe0453a, light: 0xff9a8a, dark: 0xa82a22 } },
];

// Pipe designs: body, highlight stripe and shadow stripe/band.
export const PIPES = [
  { id: 'green', name: { de: 'Klassisch', en: 'Classic' }, price: 0, pipe: 0x73bf2e, light: 0xb2ea6c, dark: 0x4f8a1f },
  { id: 'blue', name: { de: 'Himmelblau', en: 'Sky Blue' }, price: 250, pipe: 0x3f9be0, light: 0x9fd6ff, dark: 0x2a6fb0 },
  { id: 'orange', name: { de: 'Orange', en: 'Orange' }, price: 400, pipe: 0xf28a2e, light: 0xffc27a, dark: 0xc4611a },
  { id: 'purple', name: { de: 'Lila', en: 'Purple' }, price: 550, pipe: 0x8f63d6, light: 0xc6a8ff, dark: 0x6440a8 },
  { id: 'wood', name: { de: 'Holz', en: 'Wood' }, price: 700, pipe: 0xa8733f, light: 0xd9a86a, dark: 0x7a4f2a },
  { id: 'stone', name: { de: 'Stein', en: 'Stone' }, price: 900, pipe: 0x9aa3ad, light: 0xd0d7de, dark: 0x6a737d },
  { id: 'candy', name: { de: 'Bonbon', en: 'Candy' }, price: 1100, pipe: 0xff6fa8, light: 0xffffff, dark: 0xd9407c },
  { id: 'ice', name: { de: 'Eis', en: 'Ice' }, price: 1300, pipe: 0x5fc6e6, light: 0xe6fbff, dark: 0x3a93b8 },
  { id: 'lava', name: { de: 'Lava', en: 'Lava' }, price: 1600, pipe: 0xe0452e, light: 0xffc93c, dark: 0x8f2418 },
  { id: 'gold', name: { de: 'Gold', en: 'Gold' }, price: 2200, pipe: 0xffc629, light: 0xfff1b0, dark: 0xd99a00 },
];

export const CATALOG = {
  skin: SKINS,
  pattern: PATTERNS,
  hat: HATS,
  eyes: EYES,
  beak: BEAKS,
  trail: TRAILS,
  world: WORLDS,
  pipe: PIPES,
};
export const KINDS = Object.keys(CATALOG);

// Upgrades: each power-up can be improved in three steps.
export const UPGRADES = [
  { id: 'star', icon: 'rainbow', name: { de: 'Regenbogen', en: 'Rainbow' }, text: { de: '+1,5 s unverwundbar pro Stufe', en: '+1.5 s invincible per level' }, prices: [300, 800, 1800] },
  { id: 'magnet', icon: 'magnet', name: { de: 'Magnet', en: 'Magnet' }, text: { de: '+3 s und größere Reichweite pro Stufe', en: '+3 s and wider reach per level' }, prices: [300, 800, 1800] },
  { id: 'mini', icon: 'mushroom', name: { de: 'Mini-Vogel', en: 'Mini Bird' }, text: { de: '+3 s klein pro Stufe', en: '+3 s tiny per level' }, prices: [300, 800, 1800] },
  { id: 'luck', icon: 'clover', name: { de: 'Glückspilz', en: 'Lucky' }, text: { de: 'Power-ups tauchen öfter auf', en: 'Power-ups show up more often' }, prices: [500, 1200, 2500] },
];
export const UPGRADE_MAX = 3;
