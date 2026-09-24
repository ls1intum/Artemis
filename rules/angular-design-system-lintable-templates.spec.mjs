import { mkdtempSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { cwd } from 'node:process';
import { describe, expect, it } from 'vitest';
import { ESLint } from 'eslint';
import tsParser from '@typescript-eslint/parser';
import angular from 'angular-eslint';
import { requireLintableTemplates } from './angular-design-system-lintable-templates.mjs';

async function lint(code, { root, filePath = 'example.component.ts', parserOptions = {} } = {}) {
    const eslint = new ESLint({
        cwd: root,
        overrideConfigFile: true,
        overrideConfig: [
            {
                files: ['**/*.ts'],
                languageOptions: { parser: tsParser, parserOptions },
                processor: angular.processInlineTemplates,
                plugins: { design: { rules: { 'require-lintable-templates': requireLintableTemplates } } },
                rules: { 'design/require-lintable-templates': 'error' },
            },
            {
                files: ['**/*.html'],
                languageOptions: { parser: angular.templateParser },
                plugins: { probe: { rules: { extracted: { create: (context) => ({ Program: (node) => context.report({ node, message: 'Extracted by native processor.' }) }) } } } },
                rules: { 'probe/extracted': 'error' },
            },
        ],
    });
    const [result] = await eslint.lintText(code, { filePath });
    return result.messages;
}

const template = '<button tumUiButton class="p-4"></button>';

describe('native Angular inline-template extraction boundary', () => {
    it.each([
        `import {Component as View} from '@angular/core'; @View({template:${JSON.stringify(template)}}) class Example {}`,
        `import * as ng from '@angular/core'; @ng.Component({template:${JSON.stringify(template)}}) class Example {}`,
        `import {Component} from '@angular/core'; const META={template:${JSON.stringify(template)}}; @Component(META) class Example {}`,
        `import {Component} from '@angular/core'; const META={template:${JSON.stringify(template)}}; @Component({...META}) class Example {}`,
        `import {Component} from '@angular/core'; @Component({'template':${JSON.stringify(template)}}) class Example {}`,
        `import {Component} from '@angular/core'; @Component({['template']:${JSON.stringify(template)}}) class Example {}`,
        `import {Component} from '@angular/core'; const HTML=${JSON.stringify(template)}; @Component({template:HTML}) class Example {}`,
        `import {Component} from '@angular/core'; @Component(({template:${JSON.stringify(template)}})) class Example {}`,
        `import {Component} from '@angular/core'; @Component({template:makeTemplate()}) class Example {}`,
    ])('reports a known inline template the standard processor skips', async (code) => {
        expect(await lint(code)).toEqual([expect.objectContaining({ ruleId: 'design/require-lintable-templates', messageId: 'unsupported' })]);
    });

    it.each([JSON.stringify(template), '`' + template + '`'])('keeps canonical literal templates with the actual framework processor', async (literal) => {
        const messages = await lint(`import {Component} from '@angular/core'; @Component({template:${literal}}) class Example {}`);
        expect(messages).toEqual([expect.objectContaining({ ruleId: 'probe/extracted' })]);
    });

    it('rejects substitutions even though the processor extracts their unevaluated source text', async () => {
        const messages = await lint("import {Component} from '@angular/core'; @Component({template:`<div>${content}</div>`}) class Example {}");
        expect(messages).toContainEqual(expect.objectContaining({ ruleId: 'design/require-lintable-templates' }));
    });

    it.each([
        "import {Component as View} from '@angular/core'; @View({templateUrl:'example.html'}) class Example {}",
        "import {Component} from '@angular/core'; @Component(META) class Example {}",
        "import {Component} from '@angular/core'; @Component({...unknown}) class Example {}",
        "import {Component} from 'other-library'; @Component(META) class Example {}",
    ])('permits external templates and leaves opaque metadata to its dedicated rule', async (code) => {
        expect(await lint(code)).toEqual([]);
    });

    it('requires a direct Angular import for inline templates regardless of barrel filenames', async () => {
        const root = mkdtempSync(join(tmpdir(), 'inline-template-barrel-'));
        try {
            symlinkSync(join(cwd(), 'node_modules'), join(root, 'node_modules'), 'dir');
            const project = join(root, 'tsconfig.json');
            writeFileSync(
                project,
                JSON.stringify({ compilerOptions: { module: 'ESNext', moduleResolution: 'Bundler', experimentalDecorators: true, skipLibCheck: true }, include: ['*.ts'] }),
            );
            writeFileSync(join(root, 'barrel.ts'), "export {Component} from '@angular/core';");
            const inline = `import {Component} from './barrel'; @Component({template:${JSON.stringify(template)}}) class Example {}`;
            const external = "import {Component} from './barrel'; @Component({templateUrl:'example.html'}) class Example {}";
            const direct = `import {Component} from '@angular/core'; @Component({template:${JSON.stringify(template)}}) class Example {}`;
            for (const [name, code] of [
                ['view.ts', inline],
                ['external.ts', external],
                ['direct.ts', direct],
            ])
                writeFileSync(join(root, name), code);
            const options = (name) => ({ root, filePath: join(root, name), parserOptions: { project } });
            expect(await lint(inline, options('view.ts'))).toEqual([expect.objectContaining({ ruleId: 'design/require-lintable-templates', messageId: 'unsupported' })]);
            expect(await lint(external, options('external.ts'))).toEqual([]);
            expect(await lint(direct, options('direct.ts'))).toEqual([expect.objectContaining({ ruleId: 'probe/extracted' })]);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });
});
