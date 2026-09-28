// Shared helpers for the engine-*.mjs golden generators: loads three.js 0.186 from
// THREE_DIR (a node_modules/three directory; three is not a dependency of this repo)
// and rounds Float32 values for compact JSON.
//   npm i --prefix /tmp/three three@0.186
//   THREE_DIR=/tmp/three/node_modules/three node scripts/native-golden/engine-geometry.mjs
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const dir = process.env.THREE_DIR;
if (!dir) {
  console.error('Set THREE_DIR to a node_modules/three directory (three@0.186).');
  process.exit(1);
}
export const THREE = await import(pathToFileURL(path.join(dir, 'build/three.module.js')).href);
export const { mergeGeometries } = await import(pathToFileURL(path.join(dir, 'examples/jsm/utils/BufferGeometryUtils.js')).href);
if (!THREE.REVISION.startsWith('186')) console.warn(`warning: three r${THREE.REVISION}, fixtures expect r186`);

// Float32 values printed with 9 significant digits round-trip exactly to the same float.
export const f = (v) => Number(Math.fround(v).toPrecision(9));
export const d = (v) => Number(v.toPrecision(15));
export const arr = (a, fn = f) => Array.from(a, fn);
export const outPath = (name) => new URL(`../../docs/native/golden/${name}`, import.meta.url);
