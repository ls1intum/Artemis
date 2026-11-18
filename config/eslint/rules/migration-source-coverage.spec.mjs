import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import eslintConfig from '../../../eslint.config.mjs';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');

function normalizeMigrationPath(p) {
    if (p.includes('packages/tum-aet-ui/src/lib') || p.includes('packages/tum-aet-ui/dist') || p.includes('fesm2022')) {
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

    const stylelintConfig = JSON.parse(readFileSync(resolve(repoRoot, 'config/stylelint/stylelint.config.json'), 'utf8'));
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

    const isCovered = (path) => sourcePaths.some((sourcePath) => path === sourcePath || path.startsWith(`${sourcePath}/`));

    it('every locked path is scanned by a tailwind @source entry (@source may be a superset)', () => {
        const uncovered = lockedPaths.filter((path) => path !== '@tumaet/ui-angular' && !isCovered(path));
        expect(uncovered, `locked paths missing from tailwind.css @source (their Tailwind utilities would silently not generate): ${uncovered.join(', ')}`).toEqual([]);
    });

    it('every path of the ambiguous-spacing scope is scanned by a tailwind @source entry', () => {
        const spacingBlock = eslintConfig.find((c) => c.rules && c.rules['localRules/no-ambiguous-spacing-utility']);
        expect(spacingBlock, 'config block enabling localRules/no-ambiguous-spacing-utility').toBeTruthy();
        const spacingPaths = spacingBlock.files.map(normalizeMigrationPath);
        expect(spacingPaths.length, 'ambiguous-spacing scoped paths').toBeGreaterThan(0);
        // The rule tells authors to write a Tailwind utility, which only exists if tailwind.css scans the template.
        const uncovered = spacingPaths.filter((path) => !isCovered(path));
        expect(
            uncovered,
            `ambiguous-spacing paths missing from tailwind.css @source (the utilities the rule asks for would silently not generate): ${uncovered.join(', ')}`,
        ).toEqual([]);
    });
});
