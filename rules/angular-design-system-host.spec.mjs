import { mkdtempSync, mkdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { cwd } from 'node:process';
import { beforeAll, afterAll, describe, expect, it } from 'vitest';
import { Linter } from 'eslint';
import tsParser from '@typescript-eslint/parser';
import { createAngularDesignSystemPlugin } from './angular-design-system.mjs';

let root;
let plugin;
beforeAll(() => {
    root = mkdtempSync(join(tmpdir(), 'angular-host-policy-'));
    mkdirSync(join(root, 'components'));
    symlinkSync(join(cwd(), 'node_modules'), join(root, 'node_modules'), 'dir');
    writeFileSync(join(root, 'package.json'), '{"type":"module"}');
    writeFileSync(join(root, 'theme.css'), '@import "tailwindcss";');
    writeFileSync(
        join(root, 'components/button.ts'),
        `import {Component, input} from '@angular/core';
@Component({selector:'ds-button, button[dsButton], a[dsButton]', template:''})
export class Button { size = input<'small'|'large'>('small'); }`,
    );
    plugin = createAngularDesignSystemPlugin({ root, components: ['components'], theme: 'theme.css' });
});
afterAll(() => rmSync(root, { recursive: true, force: true }));

function lint(code, rule, options = {}) {
    return new Linter({ cwd: root }).verify(
        code,
        [
            {
                files: ['**/*.ts'],
                languageOptions: { parser: tsParser },
                plugins: { design: plugin },
                rules: { [`design/${rule}`]: ['error', options] },
            },
        ],
        { filename: join(root, 'consumer.ts') },
    );
}
function directive(host, members = '') {
    return `import {Directive} from '@angular/core';
@Directive({ selector: 'button[dsButton][extra]', host: ${host} })
class Extra { ${members} }`;
}

describe('upstream rules on protected Angular host bindings', () => {
    it.each([
        ['no-restyle', `{ class: 'p-4' }`, 'spacingClassWithSizes'],
        ['no-raw-colors', `{ class: 'bg-red-500' }`, 'paletteClass'],
        ['no-arbitrary-values', `{ class: 'p-[13px]' }`, 'arbitraryValueWithScale'],
        ['no-unknown-classes', `{ class: 'flex-cols' }`, 'unknownClassSuggest'],
        ['require-static-classes', `{ '[class]': 'getClasses()' }`, 'dynamicClasses'],
        ['no-inline-styles', `{ style: 'padding: 1rem' }`, 'inlineStyle'],
    ])('%s uses the actual upstream verdict', (rule, host, messageId) => {
        const result = lint(directive(host), rule);
        expect(result).toHaveLength(1);
        expect(result[0]).toMatchObject({ messageId, line: 2 });
    });

    it.each([
        [`{ '[class]': "active ? 'p-4' : 'm-4'" }`, ''],
        [`{ '[class.p-4]': 'active' }`, ''],
        [`{ '[attr.class]': "'p-4'" }`, ''],
        [`{ '[class]': 'classes' }`, `readonly classes = 'p-4';`],
        [`{ '[class]': "{'p-4': active}" }`, ''],
        [`{ class: CLASSES }`, ``, `const CLASSES = 'p-4';`],
    ])('reads native host class forms %s', (host, members, constants = '') => {
        const code = `${constants}\n${directive(host, members)}`;
        expect(lint(code, 'no-restyle', { allow: ['layout'] })).toEqual([expect.objectContaining({ messageId: 'spacingClassWithSizes' })]);
    });

    it.each(['[style.padding.px]', '[style.backgroundColor]', '[attr.style]'])('checks host style form %s', (key) => {
        expect(lint(directive(`{ '${key}': "'padding: 2rem'" }`), 'no-inline-styles')).toEqual([expect.objectContaining({ messageId: 'inlineStyle' })]);
    });

    it.each([
        `import { Directive as Enhance } from '@angular/core'; @Enhance({selector:'button[dsButton][extra]',host:{class:'p-4'}}) class Extra {}`,
        `import * as ng from '@angular/core'; @ng.Component({selector:'ds-button[extra]',host:{class:'p-4'},template:''}) class Extra {}`,
        `import {Directive} from '@angular/core'; const META = {selector:'a[dsButton][extra]',host:{class:'p-4'}} as const; @Directive(META) class Extra {}`,
    ])('recognizes Angular aliases/namespaces/static metadata', (code) => {
        expect(lint(code, 'no-restyle', { allow: ['layout'] })).toEqual([expect.objectContaining({ messageId: 'spacingClassWithSizes' })]);
    });

    it.each([
        `import {Directive, HostBinding as Bind} from '@angular/core'; @Directive({selector:'button[dsButton][extra]'}) class Extra { @Bind('class') readonly classes = 'p-4'; }`,
        `import * as ng from '@angular/core'; @ng.Directive({selector:'button[dsButton][extra]'}) class Extra { @ng.HostBinding('class.p-4') active = true; }`,
    ])('checks legacy Angular HostBinding decorators at their real TS locations', (code) => {
        const result = lint(code, 'no-restyle');
        expect(result).toHaveLength(1);
        expect(result[0].messageId).toBe('spacingClassWithSizes');
        expect(result[0].column).toBe(code.indexOf('@', code.indexOf('class Extra')) + 1);
    });

    it('checks legacy style HostBinding without evaluating its getter', () => {
        const code = `import {Directive, HostBinding} from '@angular/core'; @Directive({selector:'button[dsButton][extra]'}) class Extra { @HostBinding('style.padding') get padding() {return '2rem';} }`;
        expect(lint(code, 'no-inline-styles')).toEqual([expect.objectContaining({ messageId: 'inlineStyle' })]);
    });

    it.each([`{class: buildClasses()}`, `{...dynamicHost}`, `dynamicHost`, `{ '[class]': 'classes' }`, `{ '[class]': 'active | pipe' }`])(
        'reports unverified protected classes instead of silently trusting them: %s',
        (host) => {
            expect(lint(directive(host, `classes = 'p-4';`), 'require-static-classes')).toEqual([expect.objectContaining({ messageId: 'dynamicClasses' })]);
        },
    );

    it.each([
        `import {Component} from '@angular/core'; @Component({selector:'app-widget',host:{class:'p-4',style:'color:red'},template:''}) class Widget {}`,
        `import {Directive} from '@angular/core'; @Directive({selector:'[extra]',host:dynamicHost}) class Extra {}`,
        `import {Directive,HostBinding} from 'other-library'; @Directive({selector:'button[dsButton][extra]',host:{class:'p-4'}}) class Extra {}`,
    ])('does not apply protected-control policy to ordinary or unrelated hosts', (code) => {
        expect(lint(code, 'no-restyle')).toEqual([]);
        expect(lint(code, 'no-inline-styles')).toEqual([]);
        expect(lint(code, 'require-static-classes')).toEqual([]);
    });

    it('matches attribute-only host selectors that identify a protected native directive', () => {
        const code = `import {Directive} from '@angular/core'; @Directive({selector:'[dsButton][extra]',host:{class:'p-4'}}) class Extra {}`;
        expect(lint(code, 'no-restyle')).toEqual([expect.objectContaining({ messageId: 'spacingClassWithSizes' })]);
    });

    it('checks literal native host color attributes without mistaking them for component inputs', () => {
        expect(lint(directive(`{ '[attr.fill]': "'#ff0000'" }`), 'no-raw-colors')).toEqual([expect.objectContaining({ messageId: 'rawColorAttribute' })]);
    });

    it('does not silently trust a metadata spread that can replace protected host bindings', () => {
        const code = `import {Directive} from '@angular/core'; @Directive({selector:'button[dsButton][extra]', ...metadata}) class Extra {}`;
        expect(lint(code, 'require-static-classes')).toEqual([expect.objectContaining({ messageId: 'dynamicClasses' })]);
    });

    it('allows host layout under the same upstream policy as templates', () => {
        expect(lint(directive(`{ class:'mt-4 w-full', style:'width:100%' }`), 'no-restyle', { allow: ['layout'] })).toEqual([]);
        expect(lint(directive(`{ style:'width:100%' }`), 'no-inline-styles', { allow: ['width'] })).toEqual([]);
    });
});
