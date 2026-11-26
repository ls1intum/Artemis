import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

const script = resolve(dirname(fileURLToPath(import.meta.url)), 'migrate-tum-aet-ui-imports.mjs');

describe('migrate-tum-aet-ui-imports', () => {
    let directory;

    beforeEach(() => {
        directory = mkdtempSync(join(tmpdir(), 'migrate-ui-imports-'));
    });

    afterEach(() => {
        rmSync(directory, { recursive: true, force: true });
    });

    function run(file, ...flags) {
        return spawnSync(process.execPath, [script, ...flags, file], { encoding: 'utf8' });
    }

    function write(name, content) {
        const file = join(directory, name);
        writeFileSync(file, content);
        return file;
    }

    function syntaxErrors(text) {
        const result = ts.transpileModule(text, { reportDiagnostics: true, compilerOptions: { target: ts.ScriptTarget.ESNext } });
        return result.diagnostics.map((diagnostic) => ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'));
    }

    it('splits an import that spans several entry points into one valid statement per entry point', () => {
        const file = write('several.ts', `import { TumAetUiDialogComponent, TumAetUiButtonComponent as Button } from '@tumaet/ui-angular';\n`);

        const result = run(file);
        const output = readFileSync(file, 'utf8');

        expect(result.status).toBe(0);
        expect(output).toBe(
            `import { TumAetUiButtonComponent as Button } from '@tumaet/ui-angular/button';\nimport { TumAetUiDialogComponent } from '@tumaet/ui-angular/dialog';\n`,
        );
        // a literal backslash and "n" between the statements would not parse
        expect(output).not.toContain('\\n');
        expect(syntaxErrors(output)).toEqual([]);
    });

    it('keeps the type keyword of a type-only import and rewrites re-exports', () => {
        const file = write(
            'types.ts',
            `import type { TumAetUiDialogComponent, TumAetUiButtonComponent } from '@tumaet/ui-angular';\nexport { TumAetUiButtonComponent } from '@tumaet/ui-angular';\n`,
        );

        expect(run(file).status).toBe(0);
        const output = readFileSync(file, 'utf8');

        expect(output).toContain(`import type { TumAetUiButtonComponent } from '@tumaet/ui-angular/button';`);
        expect(output).toContain(`import type { TumAetUiDialogComponent } from '@tumaet/ui-angular/dialog';`);
        expect(output).toContain(`export { TumAetUiButtonComponent } from '@tumaet/ui-angular/button';`);
        expect(syntaxErrors(output)).toEqual([]);
    });

    it('reports a file that still imports the package root with --check and leaves it unchanged', () => {
        const content = `import { TumAetUiButtonComponent } from '@tumaet/ui-angular';\n`;
        const file = write('root.ts', content);

        const result = run(file, '--check');

        expect(result.status).toBe(1);
        expect(result.stderr).toContain('imports @tumaet/ui-angular');
        expect(readFileSync(file, 'utf8')).toBe(content);
    });

    it('accepts files that already import from entry points', () => {
        const file = write('entry.ts', `import { TumAetUiButtonComponent } from '@tumaet/ui-angular/button';\n`);

        expect(run(file, '--check').status).toBe(0);
    });

    it.each([
        ['only an unknown symbol', `import { NotAKitSymbol } from '@tumaet/ui-angular';\n`],
        ['an unknown symbol next to a known one', `import { NotAKitSymbol, TumAetUiButtonComponent } from '@tumaet/ui-angular';\n`],
    ])('fails on %s and leaves the statement as it is instead of dropping the name', (_description, content) => {
        const file = write('unknown.ts', content);

        const result = run(file);

        expect(result.status).toBe(1);
        expect(result.stderr).toContain(`'NotAKitSymbol' is not exported by an entry point`);
        expect(readFileSync(file, 'utf8')).toBe(content);
    });
});
