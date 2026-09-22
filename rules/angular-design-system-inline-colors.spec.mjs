import { beforeAll, afterAll, describe, expect, it } from 'vitest';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
import { compile } from '@tailwindcss/node';
import postcss from 'postcss';
import { Linter, ESLint } from 'eslint';
import angular from 'angular-eslint';
import tsParser from '@typescript-eslint/parser';
import { tumUiDesignSystemRules } from './tum-ui-design-system.mjs';
import { createAngularDesignSystemPlugin } from './angular-design-system.mjs';
let root;
let tumUiDesignSystem;
beforeAll(() => {
    root = mkdtempSync(join(tmpdir(), 'inline-color-policy-'));
    mkdirSync(join(root, 'components'));
    writeFileSync(join(root, 'theme.css'), '@theme { --color-danger: red; }');
    writeFileSync(join(root, 'components/button.ts'), `import {Component} from '@angular/core'; @Component({selector:'button[tumUiButton]', template:''}) export class Button {}`);
    tumUiDesignSystem = createAngularDesignSystemPlugin({ root, components: ['components'], theme: 'theme.css', scope: 'components' });
});
afterAll(() => rmSync(root, { recursive: true, force: true }));

function lint(code, typescript = false) {
    return new Linter({ cwd: root }).verify(
        code,
        [
            {
                files: [typescript ? '**/*.ts' : '**/*.html'],
                languageOptions: { parser: typescript ? tsParser : angular.templateParser },
                plugins: { 'design-system': tumUiDesignSystem },
                rules: tumUiDesignSystemRules,
            },
        ],
        { filename: join(root, `color-policy-fixture.${typescript ? 'ts' : 'html'}`) },
    );
}

describe('application literal inline-color policy', () => {
    it.each([
        '<span style="color: red">Failed</span>',
        '<fa-icon [style.color]="\'green\'" />',
        '<div [ngStyle]="{ backgroundColor: \'#ff0000\' }"></div>',
        "<span [style.color]=\"failed ? 'red' : 'var(--success)'\"></span>",
        '<span [style]="\'color: green\'"></span>',
        '<span style="--status: red; color: var(--status)"></span>',
        '<span style="color: var(--danger, red)"></span>',
        '<span style="color: var(--danger, var(--fallback, #ff0000))"></span>',
    ])('rejects authored literal colors outside protected controls: %s', (code) => {
        expect(lint(code)).toEqual([expect.objectContaining({ ruleId: 'design-system/no-literal-inline-colors', message: expect.stringContaining('semantic class') })]);
    });

    it.each([
        '<span class="text-state-danger">Failed</span>',
        '<span style="--label: red; --count: 42"></span>',
        '<span style="color: var(--danger); margin-left: 10px"></span>',
        '<span [style.background-color]="category.color"></span>',
        '<span [style.color]="colorFor(entry)"></span>',
        '<span [style.color]="failed ? domainColor : undefined"></span>',
        '<span style="background: transparent; width: 10px; font-family: red"></span>',
        '<span [ngStyle]="{ color: item.color, width: \'10px\' }"></span>',
    ])('preserves semantic tokens, layout and runtime domain colors: %s', (code) => {
        expect(lint(code)).toEqual([]);
    });

    it('keeps the source location and does not duplicate protected-control enforcement', () => {
        expect(lint('<div>\n  <span style="color: red"></span>\n</div>')[0]).toMatchObject({ line: 2, column: 9 });
        const messages = lint('<button tumUiButton style="color: red">Failed</button>');
        expect(messages.map(({ ruleId }) => ruleId)).toEqual(['design-system/no-inline-styles']);
    });

    it('covers ordinary Angular host metadata without blocking runtime host colors', () => {
        expect(lint(`import {Component} from '@angular/core'; @Component({selector:'app-status', template:'', host:{style:'color: red'}}) export class Status {}`, true)).toEqual([
            expect.objectContaining({ ruleId: 'design-system/no-literal-inline-colors' }),
        ]);
        expect(
            lint(
                `import {Component} from '@angular/core'; @Component({selector:'app-status', template:'', host:{'[style.color]':'color'}}) export class Status { color = 'user-value'; }`,
                true,
            ),
        ).toEqual([]);
    });

    it('runs on inline component templates through the Angular inline-template processor with application policy', async () => {
        const [result] = await new ESLint({
            cwd: root,
            overrideConfigFile: true,
            overrideConfig: [
                {
                    files: ['**/*.ts'],
                    languageOptions: { parser: tsParser },
                    processor: angular.processInlineTemplates,
                    plugins: { 'design-system': tumUiDesignSystem },
                    rules: tumUiDesignSystemRules,
                },
                { files: ['**/*.html'], languageOptions: { parser: angular.templateParser }, plugins: { 'design-system': tumUiDesignSystem }, rules: tumUiDesignSystemRules },
            ],
        }).lintText(`import {Component} from '@angular/core'; @Component({selector:'app-status', template:'<span style="color: red">Failed</span>'}) export class Status {}`, {
            filePath: join(root, 'inline-color-policy-fixture.component.ts'),
        });
        expect(result.messages.filter(({ ruleId }) => ruleId === 'design-system/no-literal-inline-colors')).toHaveLength(1);
    });

    it('ships the semantic feedback utilities without depending on legacy template scanning', async () => {
        const file = resolve('src/main/webapp/tailwind.css');
        const compiler = await compile(readFileSync(file, 'utf8'), { base: dirname(file), onDependency() {} });
        const emitted = postcss.parse(compiler.build([]));
        const colors = new Map();
        emitted.walkRules((rule) => rule.walkDecls('color', (declaration) => colors.set(rule.selector, declaration.value)));
        for (const [selector, color] of [
            ['.text-state-danger', 'var(--danger)'],
            ['.text-state-success', 'var(--success)'],
            ['.text-state-warning', 'var(--warning)'],
            ['.text-muted-color', 'var(--p-text-muted-color)'],
            ['.text-color', 'var(--p-text-color)'],
        ]) {
            expect(colors.get(selector), selector).toBe(color);
        }
    });
});
