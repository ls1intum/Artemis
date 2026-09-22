import { mkdtempSync, mkdirSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
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
    root = mkdtempSync(join(tmpdir(), 'angular-inline-styles-'));
    mkdirSync(join(root, 'components'));
    symlinkSync(join(cwd(), 'node_modules'), join(root, 'node_modules'), 'dir');
    writeFileSync(join(root, 'package.json'), '{"type":"module"}');
    writeFileSync(join(root, 'theme.css'), '@import "tailwindcss";');
    writeFileSync(
        join(root, 'components/button.ts'),
        `import {Component} from '@angular/core'; @Component({selector:'ds-button, button[dsButton]',template:''}) export class Button {}`,
    );
    plugin = createAngularDesignSystemPlugin({ root, components: ['components'], theme: 'theme.css' });
});
afterAll(() => rmSync(root, { recursive: true, force: true }));

function lint(code) {
    return new Linter({ cwd: root }).verify(
        code,
        [
            {
                files: ['**/*.ts'],
                languageOptions: { parser: tsParser },
                plugins: { design: plugin },
                rules: { 'design/no-restyle-stylesheets': ['error', { propertyOptions: { allow: ['width', 'margin-*'] }, privateClassPrefix: 'ds-' }] },
            },
        ],
        { filename: join(root, 'consumer.ts') },
    );
}
const component = (styles) => `import {Component} from '@angular/core';
@Component({selector:'app-widget',template:'',styles:${styles}})
class Widget {}`;

describe('Angular component inline stylesheets', () => {
    it.each([
        `['ds-button { color: red; }']`,
        '`button[dsButton] { padding: 1rem; }`',
        `['.layout { button[dsButton] { border: { color: red; } } }']`,
        `['ds-button { &:hover { background: red; } }']`,
    ])('reuses stylesheet enforcement for protected selectors: %s', (styles) => {
        const result = lint(component(styles));
        expect(result).toHaveLength(1);
        expect(result[0]).toMatchObject({ messageId: 'stylesheet', line: 2 });
    });

    it('checks private selectors even when they only set layout', () => {
        expect(lint(component(`['.ds-button-internal { width: 100%; }']`))).toEqual([expect.objectContaining({ messageId: 'stylesheet' })]);
    });

    it.each([`['.app-widget {color: red; padding: 1rem;}']`, `['ds-button {width: 100%; margin-top: 1rem;}']`, `[]`])(
        'does not ban ordinary application CSS or public host layout: %s',
        (styles) => {
            expect(lint(component(styles))).toEqual([]);
        },
    );

    it.each([
        `import {Component as View} from '@angular/core'; const CSS = 'ds-button {color:red}'; @View({selector:'app-widget',styles:[CSS]}) class Widget {}`,
        `import * as ng from '@angular/core'; const CSS = ['ds-button {color:red}']; @ng.Component({selector:'app-widget',styles:[...CSS]}) class Widget {}`,
    ])('supports real Angular decorator aliases and same-file static style references', (code) => {
        expect(lint(code)).toEqual([expect.objectContaining({ messageId: 'stylesheet' })]);
    });

    it.each(['stylesFromService()', 'IMPORTED_STYLES', '`ds-button { color: ${color} }`', '[...unknownStyles]', '42', '["broken {"]'])(
        'reports unverifiable inline styles: %s',
        (styles) => {
            expect(lint(component(styles))).toEqual([expect.objectContaining({ messageId: 'unreadable' })]);
        },
    );

    it('handles recursive constant style arrays without recursing forever', () => {
        expect(lint(`const CSS = [CSS]; ${component('CSS')}`)).toEqual([expect.objectContaining({ messageId: 'unreadable' })]);
    });

    it('checks statically computed Angular styles metadata keys', () => {
        const code = `import {Component} from '@angular/core'; const KEY = 'styles'; @Component({selector:'app-widget',[KEY]:['ds-button {color:red}']}) class Widget {}`;
        expect(lint(code)).toEqual([expect.objectContaining({ messageId: 'stylesheet' })]);
    });

    it.each([
        `const META = {styles:['ds-button {color:red}']}; @Component({...META}) class Widget {}`,
        `const KEY = 'styles'; const META = {[KEY]:['ds-button {color:red}']}; @Component(META) class Widget {}`,
        `const CSS = ['ds-button {color:red}']; const BASE = {styles:CSS}; const META = {...BASE}; @Component(META) class Widget {}`,
    ])('follows static component metadata spreads without evaluating them', (body) => {
        expect(lint(`import {Component} from '@angular/core'; ${body}`)).toEqual([expect.objectContaining({ messageId: 'stylesheet' })]);
    });

    it.each(['@Component(META) class Widget {}', '@Component({...META}) class Widget {}', '@Component({[KEY]: []}) class Widget {}'])(
        'reports metadata whose absence of inline styles cannot be established: %s',
        (body) => {
            expect(lint(`import {Component} from '@angular/core'; import {META,KEY} from './shared'; ${body}`)).toEqual([expect.objectContaining({ messageId: 'unreadable' })]);
        },
    );

    it('respects last-property precedence in static metadata spreads', () => {
        const code = `import {Component} from '@angular/core'; const META={styles:['ds-button {color:red}']}; @Component({...META,styles:[]}) class Widget {}`;
        expect(lint(code)).toEqual([]);
    });

    it('leaves external stylesheets to the normal stylesheet checks', () => {
        expect(lint(`import {Component} from '@angular/core'; @Component({selector:'app-widget',styleUrl:'widget.scss'}) class Widget {}`)).toEqual([]);
    });

    it('does not interpret decorators from another library as Angular metadata', () => {
        expect(lint(`import {Component} from 'other'; @Component({styles:externalStyles}) class Widget {}`)).toEqual([]);
    });
});

describe('typed Angular decorator identity', () => {
    it('checks re-exported Angular decorators without treating lookalike library decorators as Angular', () => {
        writeFileSync(
            join(root, 'tsconfig.json'),
            JSON.stringify({ compilerOptions: { experimentalDecorators: true, module: 'NodeNext', moduleResolution: 'NodeNext', skipLibCheck: true }, include: ['*.ts'] }),
        );
        writeFileSync(join(root, 'barrel.ts'), "export { Component as View, Directive as Behavior } from '@angular/core';");
        writeFileSync(join(root, 'fake.ts'), 'export function View(metadata: unknown): ClassDecorator { return () => {}; }');
        const source = (module) =>
            `import {View} from './${module}.js'; @View({template:'<button dsButton class="p-4"></button>', styles:['ds-button {color:red}']}) class Example {}`;
        const config = [
            {
                files: ['**/*.ts'],
                languageOptions: { parser: tsParser, parserOptions: { project: join(root, 'tsconfig.json'), tsconfigRootDir: root } },
                plugins: { design: plugin },
                rules: { 'design/require-lintable-templates': 'error', 'design/no-restyle-stylesheets': 'error', 'design/no-restyle': 'error' },
            },
        ];
        for (const name of ['barrel', 'fake']) writeFileSync(join(root, `${name}.component.ts`), source(name));
        writeFileSync(join(root, 'host.ts'), "import {Behavior} from './barrel.js'; @Behavior({selector:'button[dsButton][extra]',host:{class:'p-4'}}) class Example {}");
        const check = (file) => new Linter({ cwd: root }).verify(readFileSync(join(root, file), 'utf8'), config, { filename: join(root, file) });
        expect(
            check('barrel.component.ts')
                .map((message) => message.ruleId)
                .sort(),
        ).toEqual(['design/no-restyle-stylesheets', 'design/require-lintable-templates']);
        expect(check('host.ts')).toEqual([expect.objectContaining({ ruleId: 'design/no-restyle' })]);
        expect(check('fake.component.ts')).toEqual([]);
    });
});
