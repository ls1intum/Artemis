import process from 'node:process';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, symlinkSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { URL } from 'node:url';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { ESLint, Linter } from 'eslint';
import angular from 'angular-eslint';
import tsParser from '@typescript-eslint/parser';
import { createAngularDesignSystemPlugin } from './angular-design-system.mjs';

const referenceVerdicts = JSON.parse(readFileSync(new URL('./design-system/fixtures/upstream-verdicts.json', import.meta.url), 'utf8'));

let root;
let plugin;

function messages(code, rule, options = {}) {
    return new Linter({ cwd: root }).verify(
        code,
        [
            {
                files: ['**/*.html'],
                languageOptions: { parser: angular.templateParser },
                plugins: { design: plugin },
                rules: { [`design/${rule}`]: ['error', options] },
            },
        ],
        { filename: path.join(root, 'consumer.component.html') },
    );
}

beforeAll(() => {
    root = mkdtempSync(path.join(tmpdir(), 'artemis-design-system-'));
    mkdirSync(path.join(root, 'components'));
    symlinkSync(path.join(process.cwd(), 'node_modules'), path.join(root, 'node_modules'), 'dir');
    writeFileSync(path.join(root, 'package.json'), '{"type":"module"}');
    writeFileSync(
        path.join(root, 'theme.css'),
        '@import "tailwindcss";\n@theme { --color-brand: #123456; }\n.custom-control { color: var(--color-brand); }\n@utility fixture-layout { display: grid; }\n@custom-variant fixture-active (&[data-active]);',
    );
    writeFileSync(
        path.join(root, 'components', 'button.ts'),
        `import { Component as Control, Directive, input } from '@angular/core';
@Control({ selector: 'ds-button, button[dsButton], a[dsButton]', template: '<ng-content />' })
export class DsButton {
    size = input<'small' | 'large'>('small');
    variant = input<'solid' | 'outlined'>('solid');
}
@Control({ selector: 'ds-row', template: '<ng-content />' })
export class DsRow {}
@Directive({ selector: '[dsBehavior]' })
export class DsBehavior {}
@Directive({ selector: 'input[dsInput]', host: { class: 'control' } })
export class DsInput {}`,
    );
    writeFileSync(
        path.join(root, 'components', 'namespace.ts'),
        `import * as ng from '@angular/core';
@ng.Component({ selector: 'ds-namespace', template: '' })
export class DsNamespace { size = ng.input<'compact' | 'large'>('compact'); }`,
    );
    plugin = createAngularDesignSystemPlugin({ root, components: ['components'], theme: 'theme.css' });
});
afterAll(() => rmSync(root, { recursive: true, force: true }));

describe('upstream design-system rules through Angular', () => {
    it.each([
        ['no-restyle', '<button dsButton class="p-4"></button>', 'spacingClassWithSizes'],
        ['no-raw-colors', '<div class="bg-red-500"></div>', 'paletteClassFar'],
        ['no-arbitrary-values', '<div class="p-[13px]"></div>', 'arbitraryValueWithScale'],
        ['no-unknown-classes', '<div class="flex-cols"></div>', 'unknownClassSuggest'],
        ['require-static-classes', '<button dsButton [class]="getClasses()"></button>', 'dynamicClasses'],
        ['no-inline-styles', '<button dsButton style="padding: 1rem"></button>', 'inlineStyle'],
    ])('%s reports its upstream diagnostic', (rule, code, messageId) => {
        const result = messages(code, rule);
        expect(result).toHaveLength(1);
        expect(result[0]).toMatchObject({ ruleId: `design/${rule}`, messageId, severity: 2 });
    });

    it.each([
        ['<ds-button class="italic" />', 'appearanceClassWithVariants'],
        ['<a dsButton class="transition-all"></a>', 'appearanceClassWithVariants'],
        ['<button dsButton class="custom-control"></button>', 'declaredClass'],
        ['<input dsInput class="font-bold" />', 'appearanceClassNoVariants'],
    ])('rejects typography, motion and declared custom classes on controls: %s', (code, messageId) => {
        expect(messages(code, 'no-restyle', { allow: ['layout'] })).toEqual([expect.objectContaining({ ruleId: 'design/no-restyle', messageId })]);
    });

    it.each([
        "<button dsButton [class]=\"wide() ? 'm-4' : 'w-full'\"></button>",
        '<button dsButton [ngClass]="{\'m-4\': active()}"></button>',
        '<button dsButton [class.w-full]="wide()"></button>',
        "<button dsButton class=\"m-4 {{ wide() ? 'w-full' : 'w-auto' }}\"></button>",
        "<button dsButton [class]=\"['m-4', wide() && 'w-full']\"></button>",
    ])('accepts complete layout alternatives without evaluating conditions: %s', (code) => {
        expect(messages(code, 'no-restyle', { allow: ['layout'] })).toEqual([]);
        expect(messages(code, 'require-static-classes')).toEqual([]);
    });

    it.each([
        "<button dsButton [class]=\"active() ? 'p-4' : 'm-4'\"></button>",
        '<button dsButton [ngClass]="{\'p-4\': active()}"></button>',
        '<button dsButton [class.p-4]="active()"></button>',
        "<button dsButton class=\"m-4 {{ active() ? 'p-4' : 'w-full' }}\"></button>",
    ])('checks all statically known class alternatives: %s', (code) => {
        expect(messages(code, 'no-restyle', { allow: ['layout'] })).toEqual([expect.objectContaining({ messageId: 'spacingClassWithSizes' })]);
    });

    it.each(['<button dsButton class="bg-{{color()}}"></button>', '<button dsButton [class]="\'bg-\' + color()"></button>'])(
        'rejects incomplete class construction: %s',
        (code) => {
            expect(messages(code, 'require-static-classes')).toEqual([expect.objectContaining({ messageId: 'dynamicClasses' })]);
        },
    );

    it.each([
        '<ds-button styleClass="p-4" />',
        "<ds-button [styleClass]=\"active() ? 'p-4' : 'm-4'\" />",
        '<ds-button containerClass="p-4" />',
        '<ds-button [className]="\'p-4\'" />',
    ])('uses upstream recognition for class-bearing component inputs: %s', (code) => {
        expect(messages(code, 'no-restyle', { allow: ['layout'] })).toEqual([expect.objectContaining({ messageId: 'spacingClassWithSizes' })]);
    });

    it('does not treat unrelated class-like attribute substrings as classes', () => {
        expect(messages('<ds-button classification="p-4" aria-label="p-4" />', 'no-restyle')).toEqual([]);
        expect(messages('<ds-button [styleClass]="getClasses()" />', 'require-static-classes')).toEqual([expect.objectContaining({ messageId: 'dynamicClasses' })]);
    });

    it('keeps behavioral directives and ordinary content outside no-restyle contracts', () => {
        expect(messages('<div dsBehavior class="p-4"></div><div class="p-4"></div>', 'no-restyle')).toEqual([]);
    });

    it('derives selector and size metadata from newly added component source', () => {
        const initial = messages('<input dsField class="p-4" />', 'no-restyle');
        expect(initial).toEqual([]);
        writeFileSync(
            path.join(root, 'components', 'field.ts'),
            `import { Component, input } from '@angular/core';
@Component({ selector: 'input[dsField]:not([plain])', template: '' })
export class DsField { size = input<'compact' | 'comfortable'>('compact'); }`,
        );
        const now = Date.now();
        const clock = vi.spyOn(Date, 'now').mockReturnValue(now + 2000);
        try {
            const result = messages('<input dsField class="p-4" />', 'no-restyle');
            expect(result).toHaveLength(1);
            expect(result[0].message).toContain('compact, comfortable');
            expect(messages('<input dsField plain class="p-4" />', 'no-restyle')).toEqual([]);
        } finally {
            clock.mockRestore();
        }
    });

    it('discovers components and signal inputs from namespace Angular imports', () => {
        const result = messages('<ds-namespace class="p-4" />', 'no-restyle');
        expect(result).toEqual([expect.objectContaining({ messageId: 'spacingClassWithSizes' })]);
        expect(result[0].message).toContain('compact');
        expect(result[0].message).toContain('large');
    });

    it('directs spacing fixes to a real enclosing component whose contract permits them', () => {
        const options = { allow: ['layout'], contracts: [{ pattern: '^DsRow$', allow: ['layout', 'spacing'] }] };
        const result = messages('<ds-row><button dsButton class="p-4"></button></ds-row>', 'no-restyle', options);
        expect(result).toHaveLength(1);
        expect(result[0].message).toContain('spacing on <DsRow>');
        expect(result[0].message).not.toContain('gap on the parent');
    });

    it('honors upstream contract allow/deny precedence and diagnostic messages', () => {
        const options = { allow: ['layout'], contracts: [{ pattern: '^DsButton$', allow: ['layout', 'p-4'], deny: ['m-4'], message: 'Control owns {{className}}.' }] };
        expect(messages('<button dsButton class="p-4"></button>', 'no-restyle', options)).toEqual([]);
        expect(messages('<button dsButton class="m-4"></button>', 'no-restyle', options)).toEqual([expect.objectContaining({ message: 'Control owns m-4.' })]);
    });

    it('checks raw SVG attributes without treating component color inputs as CSS', () => {
        expect(messages('<svg><path fill="#ff0000" /></svg>', 'no-raw-colors')).toEqual([expect.objectContaining({ messageId: 'rawColorAttribute' })]);
        expect(messages('<ds-button color="red" /><svg><path fill="currentColor" /></svg>', 'no-raw-colors')).toEqual([]);
    });

    it.each(['<div style="--chart-color: #ff0000"></div>', '<div [style.--chart-color]="\'#ff0000\'"></div>', "<div [ngStyle]=\"{'--chart-color': '#ff0000'}\"></div>"])(
        'rejects raw colors laundered through custom properties: %s',
        (code) => {
            expect(messages(code, 'no-inline-styles')).toEqual([expect.objectContaining({ messageId: 'customPropColor' })]);
        },
    );

    it('rejects injected style elements instead of silently losing them during Angular parsing', () => {
        expect(messages('<style>button { color: red; }</style>', 'no-inline-styles')).toEqual([expect.objectContaining({ messageId: 'styleElement', line: 1, column: 1 })]);
        expect(messages('<div>&lt;style&gt; is text</div><!-- <style>comment</style> -->', 'no-inline-styles')).toEqual([]);
    });

    it.each(['<STYLE>button { color: red; }</STYLE>', '<svg><style>button { color: red; }</style></svg>'])(
        'rejects native style elements across case and namespaces: %s',
        (code) => {
            expect(messages(code, 'no-inline-styles')).toEqual([expect.objectContaining({ messageId: 'styleElement' })]);
        },
    );

    it('allows token-valued custom properties and explicitly allowed dynamic layout', () => {
        expect(messages('<div style="--chart-color: var(--color-brand)"></div>', 'no-inline-styles')).toEqual([]);
        expect(messages('<button dsButton [style.width.px]="width()"></button>', 'no-inline-styles', { allow: ['width'] })).toEqual([]);
        expect(messages('<div [ngStyle]="getStyles()"></div>', 'no-inline-styles')).toEqual([expect.objectContaining({ messageId: 'dynamicStyle' })]);
    });

    it('uses the project Tailwind compiler for custom utilities and unknown variants', () => {
        expect(messages('<div class="fixture-active:fixture-layout"></div>', 'no-unknown-classes')).toEqual([]);
        expect(messages('<div class="definitely-unknown-variant:flex"></div>', 'no-unknown-classes')).toEqual([expect.objectContaining({ messageId: 'unknownVariant' })]);
    });

    it.each(['style', 'attr.style'])('reads bound literal CSS in [%s] rather than reporting an opaque style object', (binding) => {
        expect(messages(`<div [${binding}]="'width: 100%'"></div>`, 'no-inline-styles', { allow: ['width'] })).toEqual([]);
        expect(messages(`<div [${binding}]="'--chart-color: #ff0000'"></div>`, 'no-inline-styles')).toEqual([expect.objectContaining({ messageId: 'customPropColor' })]);
    });

    it('reports original HTML attribute positions and never offers whole-attribute replacements', () => {
        const code = '<div>\n  <button dsButton class="p-[13px]"></button>\n</div>';
        const result = messages(code, 'no-arbitrary-values');
        expect(result).toHaveLength(1);
        expect(result[0]).toMatchObject({ line: 2, column: 20, endLine: 2, endColumn: 36 });
        expect(result[0].suggestions).toBeUndefined();
        expect(result[0].fix).toBeUndefined();
    });

    it('maps inline-template findings to the original TypeScript location', async () => {
        const eslint = new ESLint({
            cwd: root,
            overrideConfigFile: true,
            overrideConfig: [
                { files: ['**/*.ts'], languageOptions: { parser: tsParser }, processor: angular.processInlineTemplates },
                {
                    files: ['**/*.html'],
                    languageOptions: { parser: angular.templateParser },
                    plugins: { design: plugin },
                    rules: { 'design/no-restyle': ['error', { allow: ['layout'] }] },
                },
            ],
        });
        const code = `import { Component } from '@angular/core';
@Component({
    template: '<button dsButton class="p-4"></button>',
})
export class ConsumerComponent {}`;
        const [result] = await eslint.lintText(code, { filePath: path.join(root, 'consumer.component.ts') });
        expect(result.messages).toEqual([expect.objectContaining({ ruleId: 'design/no-restyle', line: 3, column: 33, endLine: 3, endColumn: 44 })]);
    });

    it.each(referenceVerdicts.cases)('matches the captured upstream $rule verdict', ({ rule, htmlAttribute, options, messages: expected }) => {
        const actual = messages(`<ds-button ${htmlAttribute} />`, rule, options);
        expect(actual.map(({ message, severity }) => ({ message, severity }))).toEqual(expected);
    });
});
