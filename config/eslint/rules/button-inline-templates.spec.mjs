import { describe, expect, it } from 'vitest';
import { ESLint } from 'eslint';
import tsParser from '@typescript-eslint/parser';
import * as templateParser from '@angular-eslint/template-parser';
import processor from './button-inline-templates.mjs';
import rule from './enforce-button-intent.mjs';
import noBootstrapClasses from './no-bootstrap-classes.mjs';

describe('button inline template enforcement', () => {
    it('checks extracted templates and maps diagnostics back without losing TypeScript checks', async () => {
        const eslint = new ESLint({
            overrideConfigFile: true,
            overrideConfig: [
                { files: ['**/*.ts'], languageOptions: { parser: tsParser }, processor, rules: { 'no-alert': 'error' } },
                {
                    files: ['**/*.button-intent'],
                    languageOptions: { parser: templateParser },
                    plugins: { product: { rules: { buttons: rule } } },
                    rules: { 'product/buttons': 'error' },
                },
            ],
        });
        const source = [
            "import { Component } from '@angular/core';",
            '@Component({',
            '  template: `',
            '    <button tumAetUiButton severity="info">Export CSV</button>',
            '  `,',
            '})',
            "class Example { run() { alert('test'); } }",
        ].join('\n');
        const [result] = await eslint.lintText(source, { filePath: 'example.component.ts' });
        expect(result.messages.map(({ ruleId, line }) => ({ ruleId, line })).sort((left, right) => left.line - right.line)).toEqual([
            { ruleId: 'product/buttons', line: 4 },
            { ruleId: 'no-alert', line: 7 },
        ]);
    });

    it('applies the migration locks to the same templates and reports each finding once, on the right line', async () => {
        const eslint = new ESLint({
            overrideConfigFile: true,
            overrideConfig: [
                { files: ['**/*.ts'], languageOptions: { parser: tsParser }, processor },
                {
                    files: ['**/*.button-intent'],
                    languageOptions: { parser: templateParser },
                    plugins: { product: { rules: { buttons: rule } } },
                    rules: { 'product/buttons': 'error' },
                },
                {
                    files: ['**/*.lock-template'],
                    languageOptions: { parser: templateParser },
                    plugins: { lock: { rules: { bootstrap: noBootstrapClasses } } },
                    rules: { 'lock/bootstrap': 'error' },
                },
            ],
        });
        const source = [
            "import { Component } from '@angular/core';",
            '@Component({',
            '  template: `',
            '    <button tumAetUiButton severity="info">Export CSV</button>',
            '    <div class="d-flex mb-3 fw-bold">text</div>',
            '  `,',
            '})',
            'class First {}',
            '@Component({',
            '  template: \'<span class="badge">second</span>\',',
            '})',
            'class Second {}',
        ].join('\n');
        const [result] = await eslint.lintText(source, { filePath: 'example.component.ts' });
        expect(result.messages.map(({ ruleId, line }) => ({ ruleId, line })).sort((left, right) => left.line - right.line || left.ruleId.localeCompare(right.ruleId))).toEqual([
            { ruleId: 'product/buttons', line: 4 },
            { ruleId: 'lock/bootstrap', line: 5 },
            { ruleId: 'lock/bootstrap', line: 5 },
            { ruleId: 'lock/bootstrap', line: 10 },
        ]);
    });
});
