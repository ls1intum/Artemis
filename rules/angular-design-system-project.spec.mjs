import { mkdtempSync, mkdirSync, writeFileSync, rmSync, symlinkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import process from 'node:process';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createComponentIndex } from './angular-design-system-project.mjs';

let root;
beforeEach(() => {
    root = mkdtempSync(path.join(tmpdir(), 'angular-metadata-'));
    symlinkSync(path.join(process.cwd(), 'node_modules'), path.join(root, 'node_modules'), 'dir');
    mkdirSync(path.join(root, 'components'));
});
afterEach(() => rmSync(root, { recursive: true, force: true }));
function source(name, code) {
    writeFileSync(path.join(root, 'components', name), code);
}
function index() {
    return createComponentIndex([path.join(root, 'components')])();
}

describe('Angular design-system metadata', () => {
    it('normalizes HTML CSS attributes without making Angular bindings case-insensitive', () => {
        source(
            'button.ts',
            `import { Directive } from '@angular/core';
@Directive({selector: 'button[dsButton]:not([noStyle])', host: {class: 'button'}}) export class Button {}`,
        );
        const components = index();
        const element = { name: 'button', attributes: [{ name: 'DSBUTTON', value: '' }], inputs: [] };
        expect(components.match(element)).toHaveLength(0);
        expect(components.match(element, { caseInsensitiveAttributes: true })).toHaveLength(1);
        element.attributes.push({ name: 'NOSTYLE', value: '' });
        expect(components.match(element, { caseInsensitiveAttributes: true })).toHaveLength(0);
    });
    it('discovers inherited signal inputs across aliased imports and source files', () => {
        source(
            'base.ts',
            `import { Directive, input as field } from '@angular/core';
@Directive() export class Base { size = field<'small' | 'large'>('small'); }`,
        );
        source(
            'child.ts',
            `import { Component } from '@angular/core'; import { Base } from './base';
@Component({selector: 'ds-child', template: ''}) export class Child extends Base {}`,
        );
        expect(index().components[0].inputs.get('size')).toEqual(expect.arrayContaining(['small', 'large']));
    });
    it('discovers legacy decorator aliases and metadata input aliases', () => {
        source(
            'button.ts',
            `import { Component, Input as In } from '@angular/core';
@Component({selector: 'ds-button', template: '', inputs: ['density: size']}) export class Button {
  @In({alias: 'variant'}) appearance: 'solid' | 'outline' = 'solid';
  density: 'compact' | 'regular' = 'regular';
}`,
        );
        const component = index().components[0];
        expect(component.inputs.get('variant')).toEqual(expect.arrayContaining(['solid', 'outline']));
        expect(component.inputs.get('size')).toEqual(expect.arrayContaining(['compact', 'regular']));
    });
    it('protects native directives styled through host style or legacy HostBinding', () => {
        source(
            'directives.ts',
            `import { Directive, HostBinding } from '@angular/core';
@Directive({selector:'[dsStyle]', host: {'[style.color]': 'color'}}) export class Styled {}
@Directive({selector:'[dsLegacy]'}) export class Legacy { @HostBinding('class') classes = 'control'; }
@Directive({selector:'[behavior]', host:{'[attr.aria-label]':'label'}}) export class Behavior {}`,
        );
        expect(
            index()
                .components.filter((component) => component.ownsAppearance)
                .map((component) => component.name),
        ).toEqual(['Styled', 'Legacy']);
    });
    it('uses last metadata properties rather than trusting an overridden selector', () => {
        source(
            'panel.ts',
            `import { Component } from '@angular/core';
@Component({selector:'ds-stale', selector:'ds-panel', template:''}) export class Panel {}`,
        );
        expect(index().components[0].selector).toBe('ds-panel');
    });
    it('inherits host appearance from an abstract directive', () => {
        source(
            'base.ts',
            `import { Directive } from '@angular/core';
@Directive({host: {'class': 'base-control'}}) export class Base {}`,
        );
        source(
            'child.ts',
            `import { Directive } from '@angular/core'; import { Base } from './base';
@Directive({selector:'button[dsInherited]'}) export class Child extends Base {}`,
        );
        expect(index().components[0].ownsAppearance).toBe(true);
    });
    it('resolves imported constant metadata spreads without evaluating code', () => {
        source('metadata.ts', `export const defaults = {selector:'ds-default', host:{class:'control'}};`);
        source(
            'panel.ts',
            `import { Directive } from '@angular/core'; import { defaults } from './metadata';
@Directive({...defaults, selector:'[dsFinal]'}) export class Panel {}`,
        );
        expect(index().components[0]).toMatchObject({ selector: '[dsFinal]', ownsAppearance: true });
    });
    it('fails explicitly when metadata spread cannot be resolved', () => {
        source(
            'panel.ts',
            `import { Component } from '@angular/core';
@Component({...getMetadata(), selector:'ds-panel'}) export class Panel {}`,
        );
        expect(index).toThrow('Cannot statically read Angular metadata spread');
    });
    it('follows decorator and signal re-exports through TypeScript symbols', () => {
        source('barrel.ts', `export { Component as View, input as field } from '@angular/core';`);
        source(
            'panel.ts',
            `import { View, field } from './barrel';
@View({selector:'ds-panel', template:''}) export class Panel {size=field<'small'|'large'>('small');}`,
        );
        expect(index().components[0].inputs.get('size')).toEqual(expect.arrayContaining(['small', 'large']));
    });
    it('reads computed literal metadata keys without textual spelling assumptions', () => {
        source(
            'panel.ts',
            `import { Directive } from '@angular/core'; const SELECTOR = 'selector'; const HOST_CLASS = 'class';
@Directive({[SELECTOR]:'[dsComputed]', host:{[HOST_CLASS]:'control'}}) export class Panel {}`,
        );
        expect(index().components[0]).toMatchObject({ selector: '[dsComputed]', ownsAppearance: true });
    });
    it('protects composed directives when their host directive owns appearance', () => {
        source(
            'directives.ts',
            `import { Directive } from '@angular/core';
@Directive({selector:'[dsBase]', host:{class:'control'}}) export class Base {}
@Directive({selector:'[dsComposed]', hostDirectives:[Base]}) export class Composed {}
@Directive({selector:'[dsExposed]', hostDirectives:[{directive:Base, inputs:[]}]}) export class Exposed {}`,
        );
        expect(
            index()
                .components.filter((component) => component.ownsAppearance)
                .map((component) => component.name),
        ).toEqual(['Base', 'Composed', 'Exposed']);
    });
});
