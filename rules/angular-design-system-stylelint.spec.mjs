import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import stylelint from 'stylelint';
import { compileString } from 'sass';
import { createDesignSystemStyleRule } from './angular-design-system-stylelint.mjs';
import { inlineStyleOptions } from './tum-ui-design-system.mjs';

let root;
let plugin;

beforeAll(() => {
    root = mkdtempSync(path.join(tmpdir(), 'artemis-style-policy-'));
    mkdirSync(path.join(root, 'components'));
    writeFileSync(
        path.join(root, 'components', 'controls.ts'),
        `import { Component, Directive } from '@angular/core';
@Component({ selector: 'ds-panel', template: '' }) export class Panel {}
@Component({ selector: 'button[dsButton], a[dsButton]', template: '' }) export class Button {}
@Directive({ selector: 'input[dsInput]:not([plain])', host: { class: 'control' } }) export class Input {}
@Directive({ selector: '[behavior]' }) export class Behavior {}`,
    );
    plugin = createDesignSystemStyleRule({ components: [path.join(root, 'components')], propertyOptions: inlineStyleOptions });
});
afterAll(() => rmSync(root, { recursive: true, force: true }));

async function lint(code, selectedPlugin = plugin) {
    const result = await stylelint.lint({
        code,
        codeFilename: path.join(root, 'consumer.scss'),
        config: {
            customSyntax: 'postcss-scss',
            plugins: [selectedPlugin],
            rules: { 'design-system/no-restyle': true },
        },
    });
    expect(result.results[0].invalidOptionWarnings).toEqual([]);
    return result.results[0].warnings;
}

describe('Angular design-system stylesheet adapter', () => {
    it.each([
        'ds-panel { padding: 1rem; }',
        'DS-PANEL { PADDING: 1rem; }',
        String.raw`ds\2d panel { padding: 1rem; }`,
        String.raw`button[ds\42 utton] { padding: 1rem; }`,
        'ds-panel.compact#sidebar:hover { opacity: 0.5; }',
        'button[dsButton].compact { border-radius: 0; }',
        '[dsButton] { text-transform: uppercase; }',
        '.page, button[dsButton] { font-weight: bold; }',
        'button[dsButton], .page { font-weight: bold; }',
        ':is(ds-panel, button[dsButton]) { padding: 1rem; }',
        ':where(button[dsButton]).compact { padding: 1rem; }',
        ':not(:not(button[dsButton])) { padding: 1rem; }',
        ':not(:not(.ordinary, ds-panel)) { padding: 1rem; }',
        ':is(.ordinary, :not(:not(button[dsButton]))) { padding: 1rem; }',
        ':not(:not(:where(button[dsButton]))) { padding: 1rem; }',
        ':nth-child(odd of ds-panel) { padding: 1rem; }',
        ':nth-last-child(2n + 1 of .ordinary, button[dsButton]) { padding: 1rem; }',
        ':NTH-CHILD(2 OF :IS(.ordinary, ds-panel)) { padding: 1rem; }',
        ':nth-child(2n+1 of .page > button[dsButton]) { padding: 1rem; }',
        'button:nth-child(2 of [dsButton]) { padding: 1rem; }',
        ':is(.ordinary, :nth-child(2 of ds-panel)) { padding: 1rem; }',
        'ds-panel { &:hover { padding: 1rem; } }',
        'ds-panel { &.compact { padding: 1rem; } }',
        '.page { button[dsButton] { padding: 1rem; } }',
        'ds-panel { @media (width > 40rem) { padding: 1rem; } }',
        'button[dsButton]:not(:hover) { padding: 1rem; }',
        'input[dsInput] { padding: 1rem; }',
        'button[DSBUTTON] { padding: 1rem; }',
        '[dsbutton] { padding: 1rem; }',
        'input[DSINPUT] { padding: 1rem; }',
    ])('rejects direct and nested appearance overrides: %s', async (code) => {
        expect(await lint(code)).toEqual([expect.objectContaining({ rule: 'design-system/no-restyle', severity: 'error' })]);
    });

    it.each([
        'ds-panel { margin: 1rem; width: 100%; }',
        'DS-PANEL { MARGIN: 1rem; WIDTH: 100%; }',
        'button[dsButton] { display: inline-flex; }',
        '.page { padding: 1rem; }',
        '[disabled] { opacity: 0.5; }',
        'button:not([dsButton]) { padding: 1rem; }',
        'a:not(.btn, [dsButton], .tab-link):hover { text-decoration: none; }',
        ':not(ds-panel) { padding: 1rem; }',
        ':not(:not(.ordinary)) { padding: 1rem; }',
        ':not(:not(:not(button[dsButton]))) { padding: 1rem; }',
        ':not(:not(button[dsButton]), :not(div)) { padding: 1rem; }',
        '.ordinary:has(:not(:not(button[dsButton]))) { padding: 1rem; }',
        ':not(:not(button[dsButton])) .ordinary { padding: 1rem; }',
        ':nth-child(2 of .ordinary) { padding: 1rem; }',
        ':nth-child(2n + 1) { padding: 1rem; }',
        ':nth-child(2 of ds-panel .ordinary) { padding: 1rem; }',
        ':nth-child(2 of :has(ds-panel)) { padding: 1rem; }',
        '.ordinary:has(:nth-child(2 of ds-panel)) { padding: 1rem; }',
        'div:nth-child(2 of ds-panel) { padding: 1rem; }',
        ':nth-child(2 of :not(ds-panel)) { padding: 1rem; }',
        '.page:has(button[dsButton]) { padding: 1rem; }',
        'ds-panel .content { padding: 1rem; }',
        'ds-panel > .content { padding: 1rem; }',
        'button[dsButton] + .sibling { padding: 1rem; }',
        ':is(ds-panel, button[dsButton]) .content { padding: 1rem; }',
        'ds-panel { .content { padding: 1rem; } }',
        'ds-panel { & + .sibling { padding: 1rem; } }',
        'input[dsInput][plain] { padding: 1rem; }',
        'input[DSINPUT][PLAIN] { padding: 1rem; }',
        '[behavior] { padding: 1rem; }',
    ])('does not confuse layout, ancestors, exclusions or behavioral directives with control appearance: %s', async (code) => {
        expect(await lint(code)).toEqual([]);
    });

    it.each([
        '.page { &:nth-child(odd of button[dsButton], ds-panel) { padding: 1rem; } }',
        'ds-panel { &:nth-last-child(2 of .ordinary) { padding: 1rem; } }',
        'ds-panel { font: { size: 4rem; } }',
        'ds-panel { font: 1rem { weight: bold; } }',
        'ds-panel { margin: { inline: 1rem; } }',
        'ds-panel { @media (width > 40rem) { font: { size: 4rem; } } }',
        'ds-panel { @at-root .page { padding: 1rem; &:hover { padding: 2rem; } } }',
        '.page { @at-root button[dsButton] { padding: 1rem; } }',
        'ds-panel { @at-root .page & { padding: 1rem; } }',
        'ds-panel { @at-root { &:hover { padding: 1rem; } } }',
        'ds-panel { @media (width > 40rem) { @at-root (without: media) { padding: 1rem; } } }',
    ])('agrees with actual Sass output for static nested properties and @at-root subjects: %s', async (code) => {
        const sourceMessages = await lint(code);
        const compiledMessages = await lint(compileString(code).css);
        expect(sourceMessages.map(({ text }) => text)).toEqual(compiledMessages.map(({ text }) => text));
    });

    it.each([
        'ds-panel { @include appearance; }',
        'ds-panel { &:hover { @extend .custom-appearance; } }',
        'ds-panel { @media (width > 40rem) { @apply p-4; } }',
        'ds-panel.#{$variant} { padding: 1rem; }',
        'button[dsButton]#{".compact"} { padding: 1rem; }',
        'button[dsButton]#{ $suffix } { padding: 1rem; }',
        'ds-panel#{".compact"} { padding: 1rem; }',
        'ds-panel { &-#{$suffix} { padding: 1rem; } }',
        'ds-panel { #{$property}: 1rem; }',
        'ds-panel { --control-color: #{$color}; }',
        'ds-panel { @at-root #{$selector} { padding: 1rem; } }',
    ])('reports unverifiable Sass only in a protected context: %s', async (code) => {
        expect(await lint(code)).toEqual([expect.objectContaining({ text: expect.stringContaining('Cannot verify') })]);
    });

    it.each([
        '.page { @include appearance; @extend .custom-appearance; }',
        '.page-#{$variant} { padding: 1rem; }',
        '.page#{".compact"} { padding: 1rem; }',
        'button:not([dsButton])#{".compact"} { padding: 1rem; }',
        'ds-panel { .content { @include appearance; } }',
        'ds-panel { @at-root .page { @include appearance; } }',
        'ds-panel { $space: 1rem; margin: $space; }',
        'button:is(ds-panel) { padding: 1rem; }',
        'ds-panel:where(button[dsButton]) { padding: 1rem; }',
    ])('does not ban unrelated Sass or impossible functional subjects: %s', async (code) => {
        expect(await lint(code)).toEqual([]);
    });

    it('does not silently accept interpolation Sass resolves to a protected subject', async () => {
        const source = 'button[dsButton]#{".compact"} { padding: 1rem; }';
        const compiled = compileString(source).css;
        expect(compiled).toContain('button[dsButton].compact');
        expect(await lint(source)).toEqual([expect.objectContaining({ text: expect.stringContaining('Cannot verify an interpolated selector') })]);
        expect(await lint(compiled)).toEqual([expect.objectContaining({ text: expect.stringContaining('Inline style sets padding') })]);
    });

    it('still protects real functional alternatives and package-owned pseudo-elements', async () => {
        expect(await lint('button:is(ds-panel, [dsButton]) { padding: 1rem; }')).toHaveLength(1);
        expect(await lint('button[dsButton]::after { background: red; }')).toHaveLength(1);
        expect(await lint('ds-panel::before { font: { size: 4rem; } }')).toHaveLength(1);
        expect(await lint('.page::before { background: red; }')).toEqual([]);
    });

    it('applies upstream raw-color policy to custom properties while accepting theme references', async () => {
        expect(await lint('ds-panel { --control-color: #ff0000; }')).toEqual([expect.objectContaining({ text: expect.stringContaining('hardcodes a color') })]);
        expect(await lint('ds-panel { --control-color: var(--color-brand); }')).toEqual([]);
    });

    it('uses upstream component contracts instead of a duplicate CSS property allowlist', async () => {
        const custom = createDesignSystemStyleRule({
            components: [path.join(root, 'components')],
            propertyOptions: {
                allow: ['margin'],
                contracts: [{ pattern: '^Panel$', allow: ['padding'], deny: ['margin'], message: 'Panel owns {{property}}.' }],
            },
        });
        expect(await lint('ds-panel { padding: 1rem; }', custom)).toEqual([]);
        expect(await lint('ds-panel { margin: 1rem; }', custom)).toEqual([expect.objectContaining({ text: 'Panel owns margin. (design-system/no-restyle)' })]);
        expect(await lint('button[dsButton] { padding: 1rem; }', custom)).toHaveLength(1);
    });

    it('reports each declaration once at its real source location, including mixed nested rules', async () => {
        const result = await lint('ds-panel {\n  padding: 1rem;\n  &:hover { opacity: 0.5; }\n  .content { padding: 2rem; }\n}');
        expect(result).toHaveLength(2);
        expect(result[0]).toMatchObject({ line: 2, column: 3, endLine: 2, endColumn: 10 });
        expect(result[1]).toMatchObject({ line: 3, column: 13 });
    });
});
