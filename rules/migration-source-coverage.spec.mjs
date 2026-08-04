import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import eslintConfig from '../eslint.config.mjs';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

function normalizeMigrationPath(p) {
    if (p.includes('packages/tum-ui/src/lib') || p.includes('packages/tum-ui/dist') || p.includes('fesm2022')) {
        return '@tumaet/ui-angular';
    }
    return p
        .replace(/^\.\/app\//, '')
        .replace(/^.*\/app\//, '')
        .replace(/\/\*\*\/\*\.(html|scss|ts)$/, '')
        .replace(/\.component\.(html|scss|ts)$/, '');
}

const sorted = (xs) => [...xs].sort();

describe('migration lock consistency', () => {
    const lockBlock = eslintConfig.find((c) => c.rules && c.rules['localRules/no-bootstrap-classes']);
    const lockedPaths = lockBlock?.files.map(normalizeMigrationPath) ?? [];

    const stylelintConfig = JSON.parse(readFileSync(resolve(repoRoot, '.stylelintrc.json'), 'utf8'));
    const hexBsOverride = stylelintConfig.overrides.find((o) => JSON.stringify(o.rules ?? {}).includes('--bs-'));
    const stylelintPaths = hexBsOverride?.files.map(normalizeMigrationPath) ?? [];

    const tailwindCss = readFileSync(resolve(repoRoot, 'src/main/webapp/tailwind.css'), 'utf8');
    const sourcePaths = [...tailwindCss.matchAll(/@source\s+'([^']+)'/g)].map((m) => normalizeMigrationPath(m[1]));

    it('parses all three lock lists non-vacuously', () => {
        expect(lockBlock, 'config block enabling localRules/no-bootstrap-classes').toBeTruthy();
        expect(hexBsOverride, 'stylelint override banning hex / --bs-').toBeTruthy();
        expect(lockedPaths.length, 'eslint no-bootstrap locked paths').toBeGreaterThan(10);
        expect(stylelintPaths.length, 'stylelint hex/--bs- override paths').toBeGreaterThan(10);
        expect(sourcePaths.length, 'tailwind @source entries').toBeGreaterThan(10);
    });

    it('the ESLint lock and the stylelint hex/--bs- override name the same modules', () => {
        expect(sorted(stylelintPaths), 'stylelint override drifted from the no-bootstrap-classes lock').toEqual(sorted(lockedPaths));
    });

    it('every locked path is scanned by a tailwind @source entry (@source may be a superset)', () => {
        const isCovered = (path) => sourcePaths.some((sourcePath) => path === sourcePath || path.startsWith(`${sourcePath}/`));
        const uncovered = lockedPaths.filter((path) => path !== '@tumaet/ui-angular' && !isCovered(path));
        expect(uncovered, `locked paths missing from tailwind.css @source (their Tailwind utilities would silently not generate): ${uncovered.join(', ')}`).toEqual([]);
    });
});
