import { mkdtempSync, mkdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { cwd } from 'node:process';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ESLint } from 'eslint';
import angular from 'angular-eslint';
import tsParser from '@typescript-eslint/parser';
import { createAngularDesignSystemPlugin } from './angular-design-system.mjs';
import { createTemplateResolver } from './angular-design-system-owner.mjs';

const reportNode = { loc: { start: { line: 4, column: 8 }, end: { line: 4, column: 20 } }, range: [40, 52] };
let root;

beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), 'angular-owner-'));
});
afterEach(() => rmSync(root, { recursive: true, force: true }));

function source(code, name = 'example.ts') {
    writeFileSync(join(root, name), code);
}
function resolver(filename = 'example.html') {
    return createTemplateResolver([root])({ physicalFilename: join(root, filename) });
}
function component(body, before = '') {
    source(`import { Component } from '@angular/core'; ${before}
@Component({ templateUrl: './example.html' }) export class Example { ${body} }`);
}

describe('Angular template owners', () => {
    it('resolves a readonly field from a non-adjacent external template and rebases diagnostics', () => {
        mkdirSync(join(root, 'templates'));
        source(
            `import { Component as View } from '@angular/core';
const TEMPLATE = './templates/view.html';
@View({ templateUrl: TEMPLATE }) export class Example { readonly classes = 'p-4'; }`,
            'unrelated-name.ts',
        );
        expect(resolver('templates/view.html')('classes', reportNode)).toMatchObject({ type: 'Literal', value: 'p-4', ...reportNode });
    });

    it('recognizes namespace Angular decorators for inline templates', () => {
        source(`import * as ng from '@angular/core';
@ng.Component({ template: '<button></button>' }) class Example { readonly classes = ['p-4', 'mt-2'] as const; }`);
        const result = resolver('example.ts')('classes', reportNode);
        expect(result.elements.map((entry) => entry.value)).toEqual(['p-4', 'mt-2']);
        expect(result.elements[0].loc).toEqual(reportNode.loc);
    });

    it('uses ESLint physical filename instead of an inline virtual filename', () => {
        source(`import { Component } from '@angular/core'; @Component({template: ''}) class Example { readonly classes = 'p-4'; }`);
        const resolve = createTemplateResolver([root])({ filename: join(root, 'example.ts/1_inline.html'), getPhysicalFilename: () => join(root, 'example.ts') });
        expect(resolve('classes', reportNode)?.value).toBe('p-4');
    });

    it('resolves one same-file const alias and preserves object class conditions as unknown', () => {
        component('readonly classes = CLASSES;', "const CLASSES = { 'p-4': condition(), 'mt-2': true } as const;");
        const result = resolver()('classes', reportNode);
        expect(result.properties.map((entry) => entry.key.value)).toEqual(['p-4', 'mt-2']);
        expect(result.properties[0].value.type).toBe('AngularDynamicValue');
    });

    it.each([
        ['readonly classes = active() ? "p-4" : "mt-2";', 'ConditionalExpression'],
        ['readonly classes = active() && "p-4";', 'LogicalExpression'],
        ['readonly classes = "p-4" || "mt-2";', 'LogicalExpression'],
        ['readonly classes = `p-4`;', 'Literal'],
    ])('preserves complete class alternatives without evaluating conditions: %s', (body, type) => {
        component(body);
        expect(resolver()('classes', reportNode)?.type).toBe(type);
    });

    it.each([
        'classes = "p-4";',
        'readonly classes = signal("p-4");',
        'get classes() { return "p-4"; }',
        'classes() { return "p-4"; }',
        'readonly classes = this.mutable;',
        'readonly classes = "p-" + size;',
        'readonly classes = ["p-4", ...other];',
        'readonly classes = { [key]: true };',
        'readonly classes = maybeClasses || "mt-2";',
        'readonly classes = `p-${size}`;',
        'readonly classes = "p-4"; constructor() { this.classes = "m-4"; }',
        'readonly classes = ["p-4"]; change() { this.classes.push("m-4"); }',
        'readonly classes = { "p-4": true }; change() { delete this.classes["p-4"]; }',
        'readonly classes = ["p-4"]; change(key) { this[key].push("m-4"); }',
        'readonly classes = ["p-4"]; change() { (this as any).classes.push("m-4"); }',
        'readonly classes = ["p-4"]; change() { mutate(this); }',
    ])('does not claim an unsafe initializer is static: %s', (body) => {
        component(body);
        expect(resolver()('classes', reportNode)).toBeUndefined();
    });

    it.each([
        'import { CLASSES } from "./elsewhere";',
        'const FIRST = "p-4"; const CLASSES = FIRST;',
        'const CLASSES = ["p-4"]; CLASSES.push("m-4");',
        'const CLASSES = { "p-4": true }; mutate(CLASSES);',
    ])('rejects imports, longer alias chains and escaped constants: %s', (before) => {
        component('readonly classes = CLASSES;', before);
        expect(resolver()('classes', reportNode)).toBeUndefined();
    });

    it('invalidates changed, added and deleted owners between template lint passes', () => {
        component('readonly classes = "p-4";');
        const resolveTemplate = createTemplateResolver([root]);
        const context = { physicalFilename: join(root, 'example.html') };
        expect(resolveTemplate(context)('classes', reportNode)?.value).toBe('p-4');
        component('readonly classes = "mt-2";');
        expect(resolveTemplate(context)('classes', reportNode)?.value).toBe('mt-2');
        source(`import { Component } from '@angular/core'; @Component({templateUrl: './example.html'}) class Other {readonly classes = 'm-4';}`, 'other.ts');
        expect(resolveTemplate(context)('classes', reportNode)).toBeUndefined();
        rmSync(join(root, 'other.ts'));
        expect(resolveTemplate(context)('classes', reportNode)?.value).toBe('mt-2');
        rmSync(join(root, 'example.ts'));
        expect(resolveTemplate(context)('classes', reportNode)).toBeUndefined();
    });

    it('does not inherit fields from a base class', () => {
        source(`import { Component } from '@angular/core'; class Base { readonly classes = 'p-4'; }
@Component({templateUrl: './example.html'}) class Example extends Base {}`);
        expect(resolver()('classes', reportNode)).toBeUndefined();
    });

    it.each([
        "import { Component } from 'other-library'; @Component({templateUrl: './example.html'}) class Example { readonly classes = 'p-4'; }",
        "import { Component } from '@angular/core'; @Component({...other, templateUrl: './example.html'}) class Example { readonly classes = 'p-4'; }",
        "import { Component } from '@angular/core'; @Component({templateUrl: template()}) class Example { readonly classes = 'p-4'; }",
        'not valid typescript }',
    ])('does not invent an Angular owner: %s', (code) => {
        source(code);
        expect(resolver()('classes', reportNode)).toBeUndefined();
    });

    it.each(['templateUrl: "./example.html"', 'template: ""'])('rejects ambiguous owners: %s', (metadata) => {
        source(`import { Component } from '@angular/core';
@Component({${metadata}}) class First { readonly classes = 'p-4'; }
@Component({${metadata}}) class Second { readonly classes = 'm-4'; }`);
        expect(resolver(metadata.startsWith('templateUrl') ? 'example.html' : 'example.ts')('classes', reportNode)).toBeUndefined();
    });
});

describe('owner resolution through the upstream rules', () => {
    it.each([
        [false, '<ds-button [class]="classes" />', "'p-4'", 'spacingClassWithSizes'],
        [true, '<ds-button [class]="classes" />', "'p-4'", 'spacingClassWithSizes'],
        [false, `@let classes = 'p-4'; <ds-button [class]="classes" />`, "'m-4'", 'dynamicClasses'],
        [false, `@for (classes of values; track classes) { <ds-button [class]="classes" /> }`, "'m-4'", 'dynamicClasses'],
        [false, `<ng-template let-classes><ds-button [class]="classes" /></ng-template>`, "'m-4'", 'dynamicClasses'],
        [false, `<div #classes></div><ds-button [class]="classes" />`, "'m-4'", 'dynamicClasses'],
        [false, `@if (value; as classes) { <ds-button [class]="classes" /> }`, "'m-4'", 'dynamicClasses'],
        [false, `@let classes = 'm-4'; <ds-button [class]="this.classes" />`, "'p-4'", 'spacingClassWithSizes'],
        [false, '<ds-button [ngClass]="classes" />', '{0: true}', 'unclassifiedClass'],
        [false, '<ds-button CLASS="p-4" />', "'m-4'", 'spacingClassWithSizes'],
        [false, '<ds-button STYLE="padding: 1rem" />', "'m-4'", 'inlineStyle'],
        [false, '<svg><stop stop-color="#ff0000" /></svg>', "'m-4'", 'rawColorAttribute'],
        [false, '<svg><feFlood flood-color="#ff0000" /></svg>', "'m-4'", 'rawColorAttribute'],
        [false, '<svg><feDiffuseLighting lighting-color="#ff0000" /></svg>', "'m-4'", 'rawColorAttribute'],
    ])('checks Angular values and scopes (inline=%s, template=%s)', async (inline, html, initializer, messageId) => {
        mkdirSync(join(root, 'components'));
        mkdirSync(join(root, 'consumers'));
        symlinkSync(join(cwd(), 'node_modules'), join(root, 'node_modules'), 'dir');
        writeFileSync(join(root, 'package.json'), '{"type":"module"}');
        writeFileSync(join(root, 'theme.css'), '@import "tailwindcss";');
        source(
            `import {Component, input} from '@angular/core'; @Component({selector:'ds-button', template:''}) export class Button { size = input<'small'|'large'>('small'); }`,
            'components/button.ts',
        );
        const code = `import {Component} from '@angular/core'; @Component({${inline ? `template: ${JSON.stringify(html)}` : "templateUrl: './example.html'"}}) export class Example { readonly classes = ${initializer}; }`;
        source(code, 'consumers/example.component.ts');
        const plugin = createAngularDesignSystemPlugin({ root, components: ['components'], sources: ['consumers'], theme: 'theme.css' });
        const eslint = new ESLint({
            cwd: root,
            overrideConfigFile: true,
            overrideConfig: [
                { files: ['**/*.ts'], languageOptions: { parser: tsParser }, processor: angular.processInlineTemplates },
                {
                    files: ['**/*.html'],
                    languageOptions: { parser: angular.templateParser },
                    plugins: { design: plugin },
                    rules: { 'design/no-restyle': 'error', 'design/require-static-classes': 'error', 'design/no-inline-styles': 'error', 'design/no-raw-colors': 'error' },
                },
            ],
        });
        const [result] = await eslint.lintText(inline ? code : html, { filePath: join(root, inline ? 'consumers/example.component.ts' : 'consumers/example.html') });
        expect(result.messages).toHaveLength(1);
        expect(result.messages[0]).toMatchObject({ messageId });
    });
});
