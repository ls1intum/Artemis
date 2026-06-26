/**
 * Guard against the Bootstrap→Tailwind migration's silent-failure mode.
 *
 * A path is added to the `no-bootstrap-classes` ESLint lock once it is Bootstrap-free and migrated to Tailwind
 * utilities. But Tailwind only GENERATES those utilities for files it actually scans — the explicit `@source`
 * allowlist in `tailwind.css` (the project uses `source(none)`, so nothing is scanned implicitly). If a path is
 * locked but missing from `@source`, its utilities silently never generate: the template looks migrated, the lint
 * passes, yet the styles are absent in the build — a hard-to-trace "styles mysteriously missing" incident.
 *
 * This test makes that impossible: every path in the `no-bootstrap-classes` lock must be covered by an `@source`
 * entry (an exact match, or an ancestor directory). `@source` may legitimately contain MORE than the lock (a
 * module can use Tailwind before it is fully Bootstrap-free), so the check is one-directional: lock ⊆ @source.
 */
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import eslintConfig from '../eslint.config.mjs';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// Normalize any path form (`src/main/webapp/app/admin/**/*.html`, `./app/admin`, a specific `*.component.html`)
// to its `app/`-relative base, stripping a trailing `/**/*.{html,scss,ts}` glob.
function toAppBase(p) {
    return p
        .replace(/^\.\/app\//, '')
        .replace(/^.*\/app\//, '')
        .replace(/\/\*\*\/\*\.(html|scss|ts)$/, '');
}

describe('migration lock ↔ tailwind @source coverage', () => {
    it('every no-bootstrap-classes locked path is scanned by a tailwind @source entry', () => {
        const lockBlock = eslintConfig.find((c) => c.rules && c.rules['localRules/no-bootstrap-classes']);
        expect(lockBlock, 'config block enabling localRules/no-bootstrap-classes').toBeTruthy();
        const lockedBases = lockBlock.files.map(toAppBase);
        // Guard against a vacuous pass if the config shape ever changes and parsing yields nothing.
        expect(lockedBases.length, 'parsed no-bootstrap-classes locked paths').toBeGreaterThan(10);

        const tailwindCss = readFileSync(resolve(repoRoot, 'src/main/webapp/tailwind.css'), 'utf8');
        // Positive `@source './app/...'` entries only — `@source not '...'` and `@source not inline("...")` are exclusions.
        const sourceBases = [...tailwindCss.matchAll(/@source\s+'([^']+)'/g)].map((m) => toAppBase(m[1]));
        expect(sourceBases.length, 'parsed tailwind @source entries').toBeGreaterThan(10);

        const isCovered = (base) => sourceBases.some((s) => base === s || base.startsWith(s + '/'));
        const uncovered = lockedBases.filter((base) => !isCovered(base));

        expect(uncovered, `locked paths missing from tailwind.css @source (their Tailwind utilities would silently not generate): ${uncovered.join(', ')}`).toEqual([]);
    });
});
