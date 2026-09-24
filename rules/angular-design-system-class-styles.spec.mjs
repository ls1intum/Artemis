import { mkdtempSync, mkdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { cwd } from 'node:process';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { Linter } from 'eslint';
import angular from 'angular-eslint';
import tsParser from '@typescript-eslint/parser';
import { createAngularDesignSystemPlugin } from './angular-design-system.mjs';

let root;
let plugin;
beforeAll(() => {
    root = mkdtempSync(join(tmpdir(), 'class-selectors-'));
    mkdirSync(join(root, 'components'));
    symlinkSync(join(cwd(), 'node_modules'), join(root, 'node_modules'), 'dir');
    writeFileSync(join(root, 'package.json'), '{"type":"module"}');
    writeFileSync(join(root, 'theme.css'), '@import "tailwindcss"; @custom-variant controls (& ds-button);');
    writeFileSync(
        join(root, 'components/button.ts'),
        `import {Component,input} from '@angular/core';
@Component({selector:'ds-button, button[dsButton]',template:''}) export class Button {size=input<'small'|'large'>('small');}`,
    );
    writeFileSync(
        join(root, 'page.ts'),
        `import {Component} from '@angular/core';
@Component({templateUrl:'./page.html'}) class Page {readonly classes='[&_ds-button]:p-4'; readonly privateClasses='[&_.ds-private-btn]:hidden';}`,
    );
    plugin = createAngularDesignSystemPlugin({ root, components: ['components'], sources: ['.'], theme: 'theme.css', scope: 'components' });
});
afterAll(() => rmSync(root, { recursive: true, force: true }));

function lint(code, typescript = false) {
    return new Linter({ cwd: root }).verify(
        code,
        [
            {
                files: [typescript ? '**/*.ts' : '**/*.html'],
                languageOptions: { parser: typescript ? tsParser : angular.templateParser },
                plugins: { design: plugin },
                rules: { 'design/no-restyle-class-selectors': ['error', { propertyOptions: { allow: ['margin', 'margin-*', 'width'] }, privateClassPrefix: 'ds-private-' }] },
            },
        ],
        { filename: join(root, typescript ? 'host.ts' : 'page.html') },
    );
}

describe('compiler-backed class selector boundaries', () => {
    it.each([
        '<div class="[&_ds-button]:p-4"></div>',
        '<div class="[&_button[dsButton]]:p-4"></div>',
        '<div class="hover:[&_ds-button]:p-4"></div>',
        '<div class="controls:p-4"></div>',
        '<div [class]="classes"></div>',
        '<div [class]="privateClasses"></div>',
        '<div class="[&_[class*=ds-private-btn]]:hidden"></div>',
        '<div [ngClass]="{\'[&_ds-button]:p-4\': active()}"></div>',
        String.raw`<div class="[&_.ds\-private-btn]:hidden"></div>`,
        String.raw`<div class="[&_.ds\00002dprivate-btn]:hidden"></div>`,
        '<div class="[&_.ds-private-btn]:hidden"></div>',
    ])('checks descendant selectors even on ordinary ancestors: %s', (code) => {
        expect(lint(code)).toEqual([expect.objectContaining({ messageId: 'restyle', severity: 2 })]);
    });

    it.each([
        '<div class="[&_ds-button]:m-4"></div>',
        '<div class="[&_button[dsButton]]:w-full"></div>',
        '<div class="[&_span]:p-4"></div>',
        '<div class="[&:has(ds-button)]:p-4"></div>',
        '<div class="[&_[data-label=ds-private-btn]]:hidden"></div>',
        `<div class="[&_[data-label='.ds-private-btn']]:hidden"></div>`,
        '<div class="hover:p-4"></div>',
        '<div [class]="getClasses()"></div>',
    ])('does not ban ordinary styling, allowed layout or opaque ordinary behavior: %s', (code) => {
        expect(lint(code)).toEqual([]);
    });

    it('includes the authored token and actionable property feedback at its source location', () => {
        const [message] = lint('<div\n class="[&_ds-button]:p-4"></div>');
        expect(message).toMatchObject({ line: 2, column: 2, endLine: 2 });
        expect(message.message).toContain('[&_ds-button]:p-4');
        expect(message.message).toContain('padding');
        expect(message.message).toContain('small');
    });

    it('deduplicates the same token within a binding without dropping separate source sites', () => {
        expect(lint('<div class="[&_ds-button]:p-4 [&_ds-button]:p-4"></div>')).toHaveLength(1);
        expect(lint('<div class="[&_ds-button]:p-4"></div>\n<div class="[&_ds-button]:p-4"></div>').map((message) => message.line)).toEqual([1, 2]);
    });

    it('checks ordinary Angular hosts through the same class adapter', () => {
        expect(
            lint(
                `import {Component} from '@angular/core';
@Component({selector:'app-page',host:{class:'[&_ds-button]:p-4'}}) class Page {}`,
                true,
            ),
        ).toEqual([expect.objectContaining({ messageId: 'restyle' })]);
    });
});
