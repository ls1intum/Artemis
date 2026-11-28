import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { applyEdits, planRewrite } from './migrate-fontawesome-imports.mjs';

const script = resolve(dirname(fileURLToPath(import.meta.url)), 'migrate-fontawesome-imports.mjs');

function rewrite(text) {
    const { edits, problems } = planRewrite('file.ts', text);
    return { output: applyEdits(text, edits), problems };
}

function syntaxErrors(text) {
    const result = ts.transpileModule(text, { reportDiagnostics: true, compilerOptions: { target: ts.ScriptTarget.ESNext } });
    return result.diagnostics.map((diagnostic) => ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'));
}

describe('migrate-fontawesome-imports', () => {
    it('imports each icon from its own module, keeps aliases and the order, and produces valid statements', () => {
        const { output, problems } = rewrite(`import { faTrash, faTimes as faClose, faPlus } from '@fortawesome/free-solid-svg-icons';\n`);

        expect(problems).toEqual([]);
        expect(output).toBe(
            `import { faTrash } from '@fortawesome/free-solid-svg-icons/faTrash';\n` +
                `import { faTimes as faClose } from '@fortawesome/free-solid-svg-icons/faTimes';\n` +
                `import { faPlus } from '@fortawesome/free-solid-svg-icons/faPlus';\n`,
        );
        expect(output).not.toContain('\\n');
        expect(syntaxErrors(output)).toEqual([]);
    });

    it('handles the regular icons, an icon whose name starts with a digit and a multi-line import', () => {
        const { output } = rewrite(
            `import {\n    faCircleCheck,\n    faCircle,\n} from '@fortawesome/free-regular-svg-icons';\nimport { fa0 } from '@fortawesome/free-solid-svg-icons';\n`,
        );

        expect(output).toBe(
            `import { faCircleCheck } from '@fortawesome/free-regular-svg-icons/faCircleCheck';\n` +
                `import { faCircle } from '@fortawesome/free-regular-svg-icons/faCircle';\n` +
                `import { fa0 } from '@fortawesome/free-solid-svg-icons/fa0';\n`,
        );
    });

    it('keeps a type-only import and a re-export in their form', () => {
        const { output } = rewrite(`import type { faTrash } from '@fortawesome/free-solid-svg-icons';\nexport { faPlus } from '@fortawesome/free-solid-svg-icons';\n`);

        expect(output).toBe(`import type { faTrash } from '@fortawesome/free-solid-svg-icons/faTrash';\nexport { faPlus } from '@fortawesome/free-solid-svg-icons/faPlus';\n`);
    });

    describe('the types that the packages re-export', () => {
        it('imports IconDefinition from the core package, where it is defined', () => {
            const { output } = rewrite(`import { IconDefinition, faTrash } from '@fortawesome/free-solid-svg-icons';\n`);

            expect(output).toBe(`import { faTrash } from '@fortawesome/free-solid-svg-icons/faTrash';\nimport { IconDefinition } from '@fortawesome/fontawesome-svg-core';\n`);
            expect(syntaxErrors(output)).toEqual([]);
        });

        it('adds the type to the import of the core package that the file has, instead of a second import', () => {
            const { output } = rewrite(
                `import { IconProp } from '@fortawesome/fontawesome-svg-core';\nimport { IconDefinition, faTrash } from '@fortawesome/free-solid-svg-icons';\n`,
            );

            expect(output).toBe(
                `import { IconDefinition, IconProp } from '@fortawesome/fontawesome-svg-core';\nimport { faTrash } from '@fortawesome/free-solid-svg-icons/faTrash';\n`,
            );
        });

        it('keeps the members of an import sorted by their local name, as the sort-imports rule of the client demands', () => {
            const merged = rewrite(
                `import { IconName, IconProp } from '@fortawesome/fontawesome-svg-core';\nimport { IconDefinition as Definition, faTrash } from '@fortawesome/free-solid-svg-icons';\n`,
            );
            const created = rewrite(`import { IconPrefix, IconName, faTrash } from '@fortawesome/free-solid-svg-icons';\n`);

            expect(merged.output).toContain(`import { IconDefinition as Definition, IconName, IconProp } from '@fortawesome/fontawesome-svg-core';`);
            expect(created.output).toContain(`import { IconName, IconPrefix } from '@fortawesome/fontawesome-svg-core';`);
        });

        it('does not import a type twice when the core import has it and the icon imports of both packages need it', () => {
            const { output } = rewrite(
                `import { IconDefinition } from '@fortawesome/fontawesome-svg-core';\n` +
                    `import { IconDefinition, faTrash } from '@fortawesome/free-solid-svg-icons';\n` +
                    `import { IconDefinition, faCircle } from '@fortawesome/free-regular-svg-icons';\n`,
            );

            expect(output.match(/IconDefinition/g)).toHaveLength(1);
            expect(output).toContain(`import { faTrash } from '@fortawesome/free-solid-svg-icons/faTrash';`);
            expect(output).toContain(`import { faCircle } from '@fortawesome/free-regular-svg-icons/faCircle';`);
        });

        it('creates one import of the core package for the types of several statements', () => {
            const { output } = rewrite(
                `import { IconDefinition, faTrash } from '@fortawesome/free-solid-svg-icons';\nimport { IconName, faCircle } from '@fortawesome/free-regular-svg-icons';\n`,
            );

            expect(output.match(/@fortawesome\/fontawesome-svg-core/g)).toHaveLength(1);
            expect(output).toContain(`import { IconDefinition, IconName } from '@fortawesome/fontawesome-svg-core';`);
            expect(syntaxErrors(output)).toEqual([]);
        });
    });

    describe('what it does not rewrite', () => {
        it.each([
            ['a name that is not an icon', `import { notAnIcon } from '@fortawesome/free-solid-svg-icons';\n`],
            ['an unknown name next to a known one', `import { notAnIcon, faTrash } from '@fortawesome/free-solid-svg-icons';\n`],
            ['a namespace import', `import * as icons from '@fortawesome/free-solid-svg-icons';\n`],
            ['a default import', `import icons from '@fortawesome/free-solid-svg-icons';\n`],
        ])('leaves %s as it is and reports a problem instead of dropping a name', (_description, text) => {
            const { output, problems } = rewrite(text);

            expect(output).toBe(text);
            expect(problems).toHaveLength(1);
        });

        it.each([
            ['an icon that is imported from its own module', `import { faTrash } from '@fortawesome/free-solid-svg-icons/faTrash';\n`],
            ['the Angular component', `import { FaIconComponent } from '@fortawesome/angular-fontawesome';\n`],
            ['the core package', `import { IconDefinition } from '@fortawesome/fontawesome-svg-core';\n`],
        ])('does not touch %s', (_description, text) => {
            const { output, problems } = rewrite(text);

            expect(output).toBe(text);
            expect(problems).toEqual([]);
        });
    });

    describe('command line', () => {
        let directory;

        beforeEach(() => {
            directory = mkdtempSync(join(tmpdir(), 'migrate-fa-imports-'));
        });

        afterEach(() => {
            rmSync(directory, { recursive: true, force: true });
        });

        function run(file, ...flags) {
            return spawnSync(process.execPath, [script, ...flags, file], { encoding: 'utf8' });
        }

        it('rewrites a file, and --check reports a file that still imports the root without changing it', () => {
            const content = `import { faTrash } from '@fortawesome/free-solid-svg-icons';\n`;
            const checked = join(directory, 'checked.ts');
            const rewritten = join(directory, 'rewritten.ts');
            writeFileSync(checked, content);
            writeFileSync(rewritten, content);

            const check = run(checked, '--check');
            const migrate = run(rewritten);

            expect(check.status).toBe(1);
            expect(check.stderr).toContain('imports the root of an icon package');
            expect(readFileSync(checked, 'utf8')).toBe(content);
            expect(migrate.status).toBe(0);
            expect(readFileSync(rewritten, 'utf8')).toBe(`import { faTrash } from '@fortawesome/free-solid-svg-icons/faTrash';\n`);
            expect(run(rewritten, '--check').status).toBe(0);
        });

        it('recognizes an import with double quotes, which prettier would turn into single quotes', () => {
            const content = `import { faTrash } from "@fortawesome/free-solid-svg-icons";\n`;
            const file = join(directory, 'double-quotes.ts');
            writeFileSync(file, content);

            const check = run(file, '--check');
            const migrate = run(file);

            expect(check.status).toBe(1);
            expect(check.stderr).toContain('imports the root of an icon package');
            expect(migrate.status).toBe(0);
            expect(readFileSync(file, 'utf8')).toContain(`from '@fortawesome/free-solid-svg-icons/faTrash';`);
            expect(run(file, '--check').status).toBe(0);
        });

        it('exits with 1 for a name it cannot place', () => {
            const file = join(directory, 'unknown.ts');
            writeFileSync(file, `import { notAnIcon } from '@fortawesome/free-solid-svg-icons';\n`);

            expect(run(file).status).toBe(1);
        });
    });
});
