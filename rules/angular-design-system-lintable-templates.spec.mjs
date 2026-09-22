import { describe, expect, it } from 'vitest';
import { ESLint } from 'eslint';
import tsParser from '@typescript-eslint/parser';
import angular from 'angular-eslint';
import { requireLintableTemplates } from './angular-design-system-lintable-templates.mjs';

async function lint(code) {
    const eslint = new ESLint({
        overrideConfigFile: true,
        overrideConfig: [
            {
                files: ['**/*.ts'],
                languageOptions: { parser: tsParser },
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
    const [result] = await eslint.lintText(code, { filePath: 'example.component.ts' });
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
});
