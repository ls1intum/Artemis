import { mkdtempSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { cwd } from 'node:process';
import { ESLint, Linter } from 'eslint';
import tsParser from '@typescript-eslint/parser';
import { describe, expect, it } from 'vitest';
import angular from 'angular-eslint';
import rule, { createTumUiPrivateClassesRule } from './tum-ui-private-classes.mjs';
import { createTemplateRuleTester, createTypeScriptRuleTester } from './rule-tester.mjs';

const tester = createTemplateRuleTester();
describe('TUM UI implementation privacy', () => {
    it('protects the reserved namespace even on ordinary ancestors', () => {
        tester.run('tum-ui-private-classes', rule, {
            valid: [
                '<div class="page-layout"></div>',
                '<div className="tum-ui-btn"></div>',
                '<div [attr.className]="\'tum-ui-btn\'"></div>',
                '<div [className]="\'page-layout\'"></div>',
                '<tum-ui-panel class="w-full" />',
                '<div class="[&>span]:hidden"></div>',
            ],
            invalid: [
                '<div class="tum-ui-btn"></div>',
                '<div CLASS="tum-ui-btn"></div>',
                '<div [class.tum-ui-btn]="active()"></div>',
                '<div [className]="\'tum-ui-btn\'"></div>',
                '<div [ngClass]="{\'tum-ui-btn\': active()}"></div>',
            ].map((code) => ({ code, errors: [{ messageId: 'internal' }] })),
        });
    });
});

describe('ordinary Angular host implementation privacy', () => {
    it('checks statically known host classes without rejecting unknown ordinary behavior', () => {
        createTypeScriptRuleTester().run('tum-ui-private-classes', rule, {
            valid: [
                "import {Component} from '@angular/core'; @Component({host:{'[class]':'classes()'}}) class Page {}",
                "import {Directive} from '@angular/core'; @Directive({host:{class:'page-layout'}}) class Page {}",
                "import {Component} from '@angular/core'; @Component({host:{className:'tum-ui-btn'}}) class Page {}",
                "import {Component} from '@angular/core'; @Component({host:{'[attr.className]':'classes'}}) class Page {readonly classes='tum-ui-btn';}",
                "import {Directive,HostBinding} from '@angular/core'; @Directive() class Page {@HostBinding('class') get classes(){return dynamic();}}",
                "import {Component} from 'other-library'; @Component({host:{class:'tum-ui-btn'}}) class Page {}",
            ],
            invalid: [
                "import {Component} from '@angular/core'; @Component({selector:'jhi-page',host:{class:'tum-ui-btn'}}) class Page {}",
                "import {Component} from '@angular/core'; @Component({host:{'[className]':'classes'}}) class Page {readonly classes='tum-ui-btn';}",
                "import {Component as View} from '@angular/core'; @View({host:{'[class.tum-ui-btn]':'active()'}}) class Page {}",
                "import * as ng from '@angular/core'; @ng.Directive({selector:'[behavior]',host:{'[attr.class]':'classes'}}) class Page {readonly classes='tum-ui-btn';}",
                "import {Component} from '@angular/core'; @Component({host:{'[class]':'active() ? classes : other()'}}) class Page {readonly classes=`tum-ui-btn`;}",
                "import {Directive,HostBinding} from '@angular/core'; @Directive() class Page {@HostBinding('class') readonly classes='tum-ui-btn';}",
                "import {Component,HostBinding} from '@angular/core'; @Component({}) class Page {@HostBinding('class') readonly classes='tum-ui-btn';}",
                "import {Component,HostBinding} from '@angular/core'; @Component({}) class Page {@HostBinding('className') readonly classes='tum-ui-btn';}",
                "import {Component,HostBinding} from '@angular/core'; @Component({}) class Page {@HostBinding('class.tum-ui-btn') get active(){return true;}}",
            ].map((code) => ({ code, errors: [{ messageId: 'internal' }] })),
        });
    });

    it('recognizes real Angular symbols through local barrels with the existing typed parser', () => {
        const root = mkdtempSync(join(tmpdir(), 'host-private-classes-'));
        try {
            symlinkSync(join(cwd(), 'node_modules'), join(root, 'node_modules'), 'dir');
            writeFileSync(
                join(root, 'tsconfig.json'),
                JSON.stringify({ compilerOptions: { module: 'ESNext', moduleResolution: 'Bundler', experimentalDecorators: true, skipLibCheck: true }, include: ['*.ts'] }),
            );
            writeFileSync(join(root, 'barrel.ts'), "export {Component as View} from '@angular/core';");
            const code = "import {View} from './barrel'; @View({host:{class:'tum-ui-btn'}}) class Page {}";
            const filename = join(root, 'consumer.ts');
            writeFileSync(filename, code);
            const messages = new Linter({ cwd: root }).verify(
                code,
                [
                    {
                        files: ['**/*.ts'],
                        languageOptions: { parser: tsParser, parserOptions: { project: join(root, 'tsconfig.json') } },
                        plugins: { local: { rules: { privacy: rule } } },
                        rules: { 'local/privacy': 'error' },
                    },
                ],
                { filename },
            );
            expect(messages).toEqual([expect.objectContaining({ ruleId: 'local/privacy', messageId: 'internal' })]);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });

    it('enables host privacy in the actual application TypeScript configuration', async () => {
        const config = await new ESLint().calculateConfigForFile('src/main/webapp/app/privacy-check.component.ts');
        expect(config.rules['localRules/tum-ui-private-classes']).toEqual([2]);
    });
});

describe('ordinary Angular template implementation privacy', () => {
    it.each([
        ['<div [class]="classes"></div>', 'tum-ui-btn', 1],
        ['<div [ngClass]="[classes]"></div>', 'tum-ui-btn', 1],
        ['<div [className]="classes"></div>', 'tum-ui-btn', 1],
        ['<div [class]="classes"></div>', 'page-layout', 0],
        ['<div [class]="getClasses()"></div>', 'tum-ui-btn', 0],
        ['@let classes = "page-layout"; <div [class]="classes"></div>', 'tum-ui-btn', 0],
        ['@let classes = "page-layout"; <div [class]="this.classes"></div>', 'tum-ui-btn', 1],
    ])('resolves owned class values without confusing locals or dynamic behavior: %s / %s', async (template, classes, count) => {
        const root = mkdtempSync(join(tmpdir(), 'template-private-classes-'));
        try {
            writeFileSync(
                join(root, 'page.component.ts'),
                `import {Component} from '@angular/core'; @Component({templateUrl:'./page.html'}) class Page {readonly classes=${JSON.stringify(classes)};}`,
            );
            const eslint = new ESLint({
                cwd: root,
                overrideConfigFile: true,
                overrideConfig: [
                    {
                        files: ['**/*.html'],
                        languageOptions: { parser: angular.templateParser },
                        plugins: { local: { rules: { privacy: createTumUiPrivateClassesRule([root]) } } },
                        rules: { 'local/privacy': 'error' },
                    },
                ],
            });
            const [result] = await eslint.lintText(template, { filePath: join(root, 'page.html') });
            expect(result.messages).toEqual(Array.from({ length: count }, () => expect.objectContaining({ ruleId: 'local/privacy', messageId: 'internal' })));
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });
});
