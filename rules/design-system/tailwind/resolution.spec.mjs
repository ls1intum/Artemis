import { mkdtempSync, mkdirSync, writeFileSync, rmSync, symlinkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { cwd } from 'node:process';
import { setTimeout } from 'node:timers/promises';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { resolveStylesheet } from './oracle.mjs';
import { stopOracleForTests, unknownClasses } from './client.mjs';

let root;
beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), 'design-system-resolution-'));
    writeFileSync(join(root, 'package.json'), JSON.stringify({ type: 'module' }));
});
afterEach(() => {
    stopOracleForTests();
    rmSync(root, { recursive: true, force: true });
});

function stylesheetPackage(exports) {
    const directory = join(root, 'node_modules', 'fixture');
    mkdirSync(directory, { recursive: true });
    writeFileSync(join(directory, 'package.json'), JSON.stringify({ name: 'fixture', exports }));
    for (const name of ['public', 'private']) writeFileSync(join(directory, `${name}.css`), '');
    return directory;
}

describe('Tailwind dependency resolution', () => {
    it('honors package export boundaries instead of reaching into private stylesheets', () => {
        const directory = stylesheetPackage({ '.': { style: './public.css' } });
        expect(resolveStylesheet(root, 'fixture')).toBe(join(directory, 'public.css'));
        expect(resolveStylesheet(root, 'fixture/private.css')).toBeNull();
    });

    it('uses style conditions, export arrays and explicitly blocked paths', () => {
        const directory = stylesheetPackage({ '.': { style: ['./public.css'] }, './*.css': './*.css', './private.css': null });
        expect(resolveStylesheet(root, 'fixture')).toBe(join(directory, 'public.css'));
        expect(resolveStylesheet(root, 'fixture/public.css')).toBe(join(directory, 'public.css'));
        expect(resolveStylesheet(root, 'fixture/private.css')).toBeNull();
    });

    it('loads TypeScript plugins with framework resolution and invalidates transitive plugin dependencies', async () => {
        symlinkSync(join(cwd(), 'node_modules'), join(root, 'node_modules'), 'dir');
        const theme = join(root, 'theme.css');
        const helper = join(root, 'name.ts');
        writeFileSync(theme, '@import "tailwindcss"; @plugin "./plugin.ts";');
        writeFileSync(helper, 'export default "initial-control";');
        writeFileSync(
            join(root, 'plugin.ts'),
            `import name from './name';
export default ({addUtilities}: {addUtilities: Function}) => addUtilities({['.' + name]: {display: 'block'}});`,
        );
        expect(unknownClasses(theme, ['initial-control'])).toEqual([]);
        writeFileSync(helper, 'export default "updated-control";');
        // Cross the production compiler's filesystem refresh interval, not an increased test timeout.
        await setTimeout(1100);
        expect(unknownClasses(theme, ['updated-control'])).toEqual([]);
        expect(unknownClasses(theme, ['initial-control'])).toEqual([expect.objectContaining({ token: 'initial-control' })]);
    });
});
