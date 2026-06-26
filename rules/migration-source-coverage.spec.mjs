/**
 * Guard the Bootstrap→Tailwind migration's lock lists against drift and its silent-failure mode.
 *
 * Locking a migrated module touches THREE lists that must stay consistent:
 *   - the `no-bootstrap-classes` ESLint glob (eslint.config.mjs) — bans Bootstrap classes in its `.html`;
 *   - the hex/`--bs-` stylelint override (.stylelintrc.json) — bans them in its `.scss`;
 *   - the Tailwind `@source` allowlist (tailwind.css) — `source(none)` means a path's utilities ONLY generate if it
 *     is scanned, so a locked-but-unscanned path looks migrated, lints green, yet has NO styles in the build.
 *
 * Two invariants make desync impossible (both were violated in real life before this test enforced them):
 *   1. ESLint lock === stylelint override — the two locks name the SAME modules (an html-only module's stylelint
 *      glob simply matches nothing). Without this, a locked module's SCSS hex/`--bs-` is silently unguarded.
 *   2. ESLint lock ⊆ @source — every locked path is scanned. `@source` may be a SUPERSET (a partially-migrated
 *      module, e.g. `editor/markdown-editor`, is scanned for its Tailwind utilities before it is fully Bootstrap-free
 *      and lockable), so this direction is a subset check, not equality.
 */
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import eslintConfig from '../eslint.config.mjs';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// Normalize any path form — `src/main/webapp/app/admin/**/*.html`, `./app/admin`, a specific `*.component.scss` —
// to its `app/`-relative module base, stripping a trailing `/**/*.{html,scss,ts}` glob or a `.component.{html,scss,ts}`
// file suffix, so an html lock, its scss override, and its `@source` dir all reduce to the same base.
function toAppBase(p) {
    return p
        .replace(/^\.\/app\//, '')
        .replace(/^.*\/app\//, '')
        .replace(/\/\*\*\/\*\.(html|scss|ts)$/, '')
        .replace(/\.component\.(html|scss|ts)$/, '');
}

const sorted = (xs) => [...xs].sort();

describe('migration lock consistency', () => {
    const lockBlock = eslintConfig.find((c) => c.rules && c.rules['localRules/no-bootstrap-classes']);
    expect(lockBlock, 'config block enabling localRules/no-bootstrap-classes').toBeTruthy();
    const lockedBases = lockBlock.files.map(toAppBase);

    const stylelintConfig = JSON.parse(readFileSync(resolve(repoRoot, '.stylelintrc.json'), 'utf8'));
    const hexBsOverride = stylelintConfig.overrides.find((o) => JSON.stringify(o.rules ?? {}).includes('--bs-'));
    expect(hexBsOverride, 'stylelint override banning hex / --bs-').toBeTruthy();
    const stylelintBases = hexBsOverride.files.map(toAppBase);

    const tailwindCss = readFileSync(resolve(repoRoot, 'src/main/webapp/tailwind.css'), 'utf8');
    // Positive `@source './app/...'` entries only — `@source not '...'` / `@source not inline("...")` are exclusions.
    const sourceBases = [...tailwindCss.matchAll(/@source\s+'([^']+)'/g)].map((m) => toAppBase(m[1]));

    // Guard against a vacuous pass if any config shape changes and parsing yields nothing.
    it('parses all three lock lists non-vacuously', () => {
        expect(lockedBases.length, 'eslint no-bootstrap locked paths').toBeGreaterThan(10);
        expect(stylelintBases.length, 'stylelint hex/--bs- override paths').toBeGreaterThan(10);
        expect(sourceBases.length, 'tailwind @source entries').toBeGreaterThan(10);
    });

    it('the ESLint lock and the stylelint hex/--bs- override name the same modules', () => {
        expect(sorted(stylelintBases), 'stylelint override drifted from the no-bootstrap-classes lock').toEqual(sorted(lockedBases));
    });

    it('every locked path is scanned by a tailwind @source entry (@source may be a superset)', () => {
        const isCovered = (base) => sourceBases.some((s) => base === s || base.startsWith(s + '/'));
        const uncovered = lockedBases.filter((base) => !isCovered(base));
        expect(uncovered, `locked paths missing from tailwind.css @source (their Tailwind utilities would silently not generate): ${uncovered.join(', ')}`).toEqual([]);
    });
});
