// Renders the app's marketing and launcher art from the game's own bird
// model: sky with sun rays, rainbow, the bird head-on, and the "Birdy"
// wordmark. Used by scripts/render-assets.mjs through the Vite dev server.
import * as THREE from 'three';
import { createBird } from '../../src/bird.js';
import { SKINS } from '../../src/catalog.js';
import fontUrl from '../../src/fonts/lilita-one-latin.woff2?url';

const PLUM = '#543847';
const font = new FontFace('Lilita One', `url(${fontUrl})`);
const fontReady = font.load().then((f) => document.fonts.add(f));

// --- The bird, rendered once per size with a transparent background --------
const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
renderer.outputColorSpace = THREE.SRGBColorSpace;
const scene = new THREE.Scene();
scene.add(new THREE.HemisphereLight(0xffffff, 0xd8c8a8, 1.7));
const key = new THREE.DirectionalLight(0xfff6e6, 2.3);
key.position.set(2.5, 4, 6);
scene.add(key);
const rim = new THREE.DirectionalLight(0xbfe6ff, 1.2);
rim.position.set(-4, 2, 3);
scene.add(rim);
const bird = createBird();
bird.setSkin(SKINS[0]);
bird.setLook({ pattern: 'plain', hat: 'none', eyes: 'normal', beak: 'round' });
bird.animateWings(Math.PI / 2 + 0.25); // wings raised
bird.group.rotation.set(0.28, Math.PI + 0.38, 0.08); // head-on, turned a little
scene.add(bird.group);
const cam = new THREE.PerspectiveCamera(26, 1, 0.1, 50);
cam.position.set(0, 0.25, 6.2);
cam.lookAt(0, 0.05, 0);

function birdCanvas(size) {
  renderer.setPixelRatio(1);
  renderer.setSize(size, size, false);
  renderer.setClearColor(0x000000, 0);
  renderer.render(scene, cam);
  const c = document.createElement('canvas');
  c.width = c.height = size;
  c.getContext('2d').drawImage(renderer.domElement, 0, 0);
  return c;
}

// Sticker outline: the silhouette grown by `w` px in plum, under the bird.
function outlined(src, w, color = PLUM) {
  const c = document.createElement('canvas');
  c.width = src.width;
  c.height = src.height;
  const g = c.getContext('2d');
  const steps = 24;
  for (let i = 0; i < steps; i++) {
    const a = (i / steps) * Math.PI * 2;
    g.drawImage(src, Math.cos(a) * w, Math.sin(a) * w);
  }
  g.globalCompositeOperation = 'source-in';
  g.fillStyle = color;
  g.fillRect(0, 0, c.width, c.height);
  g.globalCompositeOperation = 'source-over';
  g.drawImage(src, 0, 0);
  return c;
}

// --- Background: sky, sun rays, rainbow -------------------------------------
function sky(g, w, h, cx, cy) {
  const grad = g.createLinearGradient(0, 0, 0, h);
  grad.addColorStop(0, '#3f8fe0');
  grad.addColorStop(0.55, '#8fd3f0');
  grad.addColorStop(1, '#ffe2b0');
  g.fillStyle = grad;
  g.fillRect(0, 0, w, h);
  // Soft sun rays from behind the bird.
  const r = Math.hypot(w, h);
  g.save();
  g.translate(cx, cy);
  g.fillStyle = 'rgba(255,255,255,0.13)';
  const n = 16;
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2;
    g.beginPath();
    g.moveTo(0, 0);
    g.arc(0, 0, r, a, a + Math.PI / n);
    g.closePath();
    g.fill();
  }
  const glow = g.createRadialGradient(0, 0, 0, 0, 0, r * 0.35);
  glow.addColorStop(0, 'rgba(255,250,220,0.75)');
  glow.addColorStop(1, 'rgba(255,250,220,0)');
  g.fillStyle = glow;
  g.fillRect(-r, -r, 2 * r, 2 * r);
  g.restore();
}

const BANDS = ['#ff5a5a', '#ffa43a', '#ffe14a', '#6fd86a', '#4ab8ff', '#9a7bff'];
function rainbow(g, cx, cy, radius, band) {
  g.save();
  g.lineCap = 'round';
  // Plum outline around the whole arc, then the bands.
  g.strokeStyle = PLUM;
  g.lineWidth = band * BANDS.length + band * 0.5;
  g.beginPath();
  g.arc(cx, cy, radius - (band * (BANDS.length - 1)) / 2, Math.PI * 1.02, Math.PI * 1.98);
  g.stroke();
  BANDS.forEach((col, i) => {
    g.strokeStyle = col;
    g.lineWidth = band * 1.02;
    g.beginPath();
    g.arc(cx, cy, radius - i * band, Math.PI * 1.02, Math.PI * 1.98);
    g.stroke();
  });
  g.restore();
}

// Clouds: flat rounded puffs with a plum outline (UI style).
function cloud(g, x, y, s) {
  const puffs = [[-1.1, 0.25, 0.7], [-0.35, -0.2, 0.95], [0.55, -0.05, 0.8], [1.25, 0.3, 0.6]];
  g.save();
  for (const pass of [0, 1]) {
    g.fillStyle = pass ? '#ffffff' : PLUM;
    for (const [px, py, r] of puffs) {
      g.beginPath();
      g.arc(x + px * s, y + py * s, r * s + (pass ? 0 : s * 0.12), 0, Math.PI * 2);
      g.fill();
    }
    g.fillRect(x - 1.1 * s, y + 0.25 * s - (pass ? 0 : s * 0.12), 2.35 * s, 0.7 * s + (pass ? 0 : s * 0.24));
  }
  g.restore();
}

function wordmark(g, x, y, size, align = 'center') {
  g.save();
  g.font = `${size}px 'Lilita One'`;
  g.textAlign = 'left';
  g.textBaseline = 'alphabetic';
  const a = 'Bir';
  const b = 'dy';
  const wa = g.measureText(a).width;
  const wb = g.measureText(b).width;
  let x0 = align === 'center' ? x - (wa + wb) / 2 : x;
  g.lineJoin = 'round';
  // Drop shadow + outline in plum, like the in-game title.
  g.fillStyle = PLUM;
  g.strokeStyle = PLUM;
  g.lineWidth = size * 0.16;
  g.strokeText(a, x0, y + size * 0.08);
  g.strokeText(b, x0 + wa, y + size * 0.08);
  g.strokeText(a, x0, y);
  g.strokeText(b, x0 + wa, y);
  g.fillStyle = '#fcb800';
  g.fillText(a, x0, y);
  g.fillStyle = '#73bf2e';
  g.fillText(b, x0 + wa, y);
  g.restore();
  return wa + wb;
}

function tagline(g, text, x, y, size) {
  g.save();
  g.font = `${size}px 'Lilita One'`;
  g.textAlign = 'center';
  g.lineJoin = 'round';
  g.strokeStyle = PLUM;
  g.lineWidth = size * 0.22;
  g.strokeText(text, x, y + size * 0.07);
  g.strokeText(text, x, y);
  g.fillStyle = '#ffffff';
  g.fillText(text, x, y);
  g.restore();
}

function canvas(w, h) {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  return [c, c.getContext('2d')];
}

// kinds: icon (full square), icon-bg / icon-fg (adaptive layers, 108dp with
// the 66dp safe zone), splash (w×h, any aspect), feature (1024×500).
window.renderArt = async (kind, w, h = w, opts = {}) => {
  await fontReady;
  const [c, g] = canvas(w, h);
  const s = Math.min(w, h);
  if (kind === 'icon' || kind === 'icon-bg') {
    sky(g, w, h, w / 2, h * 0.52);
    rainbow(g, w / 2, h * 0.98, s * 0.47, s * 0.052);
  }
  if (kind === 'icon' || kind === 'icon-fg') {
    // Adaptive icons get cropped to the inner 66/108: keep the bird inside.
    const k = kind === 'icon' ? 1.04 : 0.72;
    const size = Math.round(s * k);
    const b = outlined(birdCanvas(size), Math.max(2, size * 0.014));
    g.drawImage(b, (w - size) / 2, (h - size) / 2 + s * 0.02);
  }
  if (kind === 'splash') {
    sky(g, w, h, w / 2, h * 0.42);
    cloud(g, w * 0.16, h * 0.14, s * 0.06);
    cloud(g, w * 0.86, h * 0.24, s * 0.045);
    rainbow(g, w / 2, h * 0.5, s * 0.36, s * 0.036);
    const size = Math.round(s * 0.62);
    g.drawImage(outlined(birdCanvas(size), size * 0.012), (w - size) / 2, h * 0.42 - size * 0.55);
    wordmark(g, w / 2, h * 0.42 + s * 0.36, s * 0.2);
  }
  if (kind === 'feature') {
    sky(g, w, h, w * 0.72, h * 0.5);
    cloud(g, w * 0.08, h * 0.16, h * 0.07);
    cloud(g, w * 0.46, h * 0.1, h * 0.05);
    rainbow(g, w * 0.72, h * 1.02, h * 0.62, h * 0.06);
    const size = Math.round(h * 0.95);
    g.drawImage(outlined(birdCanvas(size), size * 0.012), w * 0.72 - size / 2, h * 0.02);
    wordmark(g, w * 0.3, h * 0.52, h * 0.3);
    tagline(g, opts.tagline || '', w * 0.3, h * 0.72, h * 0.075);
  }
  return c.toDataURL('image/png');
};
window.artReady = true;
