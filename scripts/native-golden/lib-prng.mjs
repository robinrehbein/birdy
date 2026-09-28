// Deterministic PRNG used ONLY to produce reproducible golden fixtures for
// functions that call Math.random() in the JS source. The real game does NOT
// seed Math.random() (browsers give no seedable RNG), so exact gate-layout
// parity between the JS build and any native port is NOT a requirement and
// is not guaranteed by these fixtures. What these fixtures freeze is the
// ALGORITHM (order of random draws, formulas, clamping) so a Kotlin port
// that plugs in its own seeded RNG at the same call sites can be checked for
// structural correctness against this trace.
//
// mulberry32: small, fast, public-domain 32-bit PRNG.
export function mulberry32(seed) {
  let a = seed >>> 0;
  return function rand() {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
