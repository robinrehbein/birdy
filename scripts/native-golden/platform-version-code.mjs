// Golden fixture for the CI-computed Play versionCode formula in
// .github/workflows/play-release.yml (the "Build signed bundle" step):
//   suffix = run_number*10 + run_attempt   (must be <= 9999)
//   versionCode = floor(epoch_seconds / 86400) * 10000 + suffix
// Re-implemented here verbatim (pure integer arithmetic) with a handful of
// sample inputs, so a Kotlin port of the release pipeline (or a check of it)
// can verify its own arithmetic against known-good outputs.
// Run: node scripts/native-golden/platform-version-code.mjs
import { writeFileSync } from 'node:fs';

function versionCode(epochSeconds, runNumber, runAttempt) {
  const suffix = runNumber * 10 + runAttempt;
  if (suffix > 9999) return null; // workflow fails the build instead
  const days = Math.floor(epochSeconds / 86400);
  return days * 10000 + suffix;
}

const cases = [
  // 2026-09-28T00:00:00Z, first attempt of run 1
  { epochSeconds: Date.parse('2026-09-28T00:00:00Z') / 1000, runNumber: 1, runAttempt: 1 },
  // Same day, run 7, attempt 2 (a retry)
  { epochSeconds: Date.parse('2026-09-28T12:34:56Z') / 1000, runNumber: 7, runAttempt: 2 },
  // One day later
  { epochSeconds: Date.parse('2026-09-29T00:00:00Z') / 1000, runNumber: 1, runAttempt: 1 },
  // Suffix overflow: run_number 1000 * 10 + 1 = 10001 > 9999 -> build must fail
  { epochSeconds: Date.parse('2026-09-28T00:00:00Z') / 1000, runNumber: 1000, runAttempt: 1 },
];

const out = cases.map((c) => ({ ...c, versionCode: versionCode(c.epochSeconds, c.runNumber, c.runAttempt) }));
writeFileSync(new URL('../../docs/native/golden/platform-version-code.json', import.meta.url), JSON.stringify(out, null, 2) + '\n');
console.log(out);
