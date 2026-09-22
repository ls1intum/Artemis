import { mkdtempSync, mkdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { cwd } from 'node:process';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { Linter } from 'eslint';
import angular from 'angular-eslint';
import { createAngularDesignSystemPlugin } from '../angular-design-system.mjs';
import * as compiler from './tailwind/client.mjs';

let root;
let plugin;
beforeAll(() => {
    root = mkdtempSync(join(tmpdir(), 'design-system-compiler-feedback-'));
    mkdirSync(join(root, 'components'));
    symlinkSync(join(cwd(), 'node_modules'), join(root, 'node_modules'), 'dir');
    writeFileSync(join(root, 'theme.css'), '@import "tailwindcss"; @plugin "./missing-plugin.js"; @theme { --color-brand: #123456; }');
    writeFileSync(join(root, 'components/button.ts'), "import {Component} from '@angular/core'; @Component({selector:'ds-button',template:''}) export class Button {}");
    plugin = createAngularDesignSystemPlugin({ root, components: ['components'], theme: 'theme.css' });
});
afterAll(() => {
    compiler.stopOracleForTests();
    rmSync(root, { recursive: true, force: true });
});

function lint(rule, code, selectedPlugin = plugin) {
    return new Linter({ cwd: root }).verify(
        code,
        [
            {
                files: ['**/*.html'],
                languageOptions: { parser: angular.templateParser },
                plugins: { design: selectedPlugin },
                rules: { [`design/${rule}`]: 'error' },
            },
        ],
        { filename: join(root, 'consumer.html') },
    );
}

const cases = [
    ['no-unknown-classes', '<ds-button class="flex block" /><ds-button class="grid" />'],
    ['no-raw-colors', '<ds-button class="text-unregisteredcustomcolor bg-unregisteredcustomcolor" /><ds-button class="border-unregisteredcustomcolor" />'],
];

describe('compiler failure diagnostics', () => {
    it.each(cases)('%s reports compiler failure once per file at the affected class binding instead of crashing', (rule, code) => {
        const result = lint(rule, code);
        expect(result).toEqual([expect.objectContaining({ ruleId: `design/${rule}`, messageId: 'compilerUnavailable', severity: 2, line: 1, column: 12 })]);
        expect(result[0].message).toContain('missing-plugin.js');
        expect(result[0].message).toContain('Fix the configured Tailwind theme');
        expect(lint(rule, code)).toHaveLength(1);
    });

    it.each(cases)('%s does not disguise unrelated programming failures as compiler diagnostics', (rule, code) => {
        const spy = vi.spyOn(compiler, 'unknownClasses').mockImplementation(() => {
            throw new TypeError('Unexpected implementation bug');
        });
        try {
            expect(() => lint(rule, code)).toThrow('Unexpected implementation bug');
        } finally {
            spy.mockRestore();
        }
    });
});

it('reports malformed imported theme CSS with its own path and coordinates, rather than crashing rule creation', () => {
    writeFileSync(join(root, 'invalid-import.css'), '@theme {\n --color-brand: red;');
    writeFileSync(join(root, 'invalid-theme.css'), '@import "tailwindcss"; @import "./invalid-import.css";');
    const invalidPlugin = createAngularDesignSystemPlugin({ root, components: ['components'], theme: 'invalid-theme.css' });
    const messages = lint('no-unknown-classes', '<ds-button class="flex" />', invalidPlugin);
    expect(messages).toEqual([expect.objectContaining({ severity: 2, message: expect.stringContaining('invalid-import.css:1:1: Unclosed block') })]);
});
