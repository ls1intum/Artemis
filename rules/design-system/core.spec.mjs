import { mkdtempSync, mkdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { cwd } from 'node:process';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { twMerge } from 'cn';
import { defaultConfig } from 'cn/config';
import { categoryOf, GROUP_CATEGORY } from './grammar/categories.mjs';
import { groupOf, unknownGroups } from './grammar/classifier.mjs';
import { projectFor, registerProject } from './project/configuration.mjs';
import { projectClassifierFor } from './project/namespaces.mjs';
import { colorTokensFor, colorValuesFor, scopedColorTokensFor, spacingBaseFor, themeFileFor } from './project/theme.mjs';
import { query } from './tailwind/oracle.mjs';
import { stopOracleForTests, unknownClasses } from './tailwind/client.mjs';

// Focused cases adapted from the MIT shadcn lint classifier, project-theme and Tailwind-oracle tests.
let root;
let theme;
let consumer;
beforeAll(() => {
    root = mkdtempSync(join(tmpdir(), 'native-design-system-core-'));
    symlinkSync(join(cwd(), 'node_modules'), join(root, 'node_modules'), 'dir');
    writeFileSync(
        join(root, 'base.css'),
        `@import 'tailwindcss';
:root { --brand-base: #123456; }
@theme inline {
    --color-brand: var(--brand-base);
    --color-removed: #000;
    --background-color-panel: #abcdef;
    --text-stat-label: 1.125rem;
    --shadow-card-glow: 0 0 2px #000;
}
@utility tap-target { min-width: 3rem; }
@custom-variant pressed (&[data-pressed]);`,
    );
    theme = join(root, 'theme.css');
    writeFileSync(theme, `@import './base.css'; @theme { --color-removed: initial; --color-local: #fff; --spacing: 0.5rem; }`);
    consumer = join(root, 'consumer.html');
    registerProject(root, theme);
});
afterAll(() => {
    stopOracleForTests();
    rmSync(root, { recursive: true, force: true });
});

describe('native design-system classification and actual project Tailwind', () => {
    it('categorizes every installed cn group and rejects uncategorized future groups', () => {
        const config = defaultConfig();
        expect(new Set(Object.keys(GROUP_CATEGORY))).toEqual(new Set(Object.keys(config.classGroups)));
        expect(unknownGroups({ classGroups: { ...config.classGroups, 'future-paint': ['future-paint'] } })).toEqual(['future-paint']);
        const cases = [
            ['text-sm/6', 'font-size', 'typography'],
            ['text-white/50', 'text-color', 'color'],
            ['[&>svg]:-mt-4!', 'mt', null],
            ['[border-inline-width:2px]', 'arbitrary..border-inline-width', 'shape'],
            ['[padding-inline:2px]', 'arbitrary..padding-inline', 'spacing'],
            ['unknown-thing', null, null],
        ];
        for (const [token, group, category] of cases) {
            expect(groupOf(token)).toBe(group);
            expect(categoryOf(groupOf(token))).toBe(category);
        }
    });

    it('classifies validator-based utilities like the installed cn engine', () => {
        const groups = [
            ['text-sm', 'text-[length:14px]', 'font-size'],
            ['text-blue-500', 'text-[color:#fff]', 'text-color'],
            ['px-2', 'px-[3rem]', 'px'],
            ['shadow-sm', 'shadow-[0_0_2px_#000]', 'shadow'],
            ['font-normal', 'font-[weight:600]', 'font-weight'],
            ['bg-red-500', 'bg-[color:#123456]', 'bg-color'],
            ['w-1/2', 'w-[calc(100%-1rem)]', 'w'],
        ];
        for (const [known, candidate, group] of groups) {
            expect(twMerge(known, candidate)).toBe(candidate);
            expect(groupOf(known)).toBe(group);
            expect(groupOf(candidate)).toBe(group);
        }
        expect(twMerge('text-sm text-blue-500')).toBe('text-sm text-blue-500');
        expect(categoryOf(groupOf('text-sm'))).toBe('typography');
        expect(categoryOf(groupOf('text-blue-500'))).toBe('color');
    });

    it('inherits imported author tokens, removes reset tokens and keeps scoped colors separate from the palette', () => {
        expect(colorTokensFor(consumer)).toEqual(new Set(['brand', 'local']));
        expect(scopedColorTokensFor(consumer).get('background-color')).toEqual(new Set(['panel']));
        expect(colorValuesFor(consumer).has('brand')).toBe(true);
        expect(spacingBaseFor(consumer)).toBe(8);
        const classifier = projectClassifierFor(consumer);
        expect(classifier.groupOf('text-stat-label')).toBe('font-size');
        expect(classifier.groupOf('shadow-card-glow')).toBe('shadow');
    });

    it('checks imported utilities, author namespaces and custom variants with real Tailwind, not the grammar alone', async () => {
        const answer = await query(theme, ['pressed:tap-target', 'bg-brand', 'bg-panel', 'text-stat-label', 'shadow-card-glow', 'data-[state=open]:flex']);
        expect(answer).toMatchObject({ ok: true, unknown: [] });
        const removed = await query(theme, ['bg-removed', 'text-panel']);
        expect(removed.ok).toBe(true);
        expect(removed.unknown.map(({ token }) => token)).toEqual(['bg-removed', 'text-panel']);
    });

    it('gets real utility and variant suggestions through the synchronous worker', () => {
        expect(unknownClasses(theme, ['pressed:tap-target', 'flex-cols', 'hovr:flex'])).toEqual([
            { token: 'flex-cols', suggestion: 'flex-col', baseKnown: false },
            { token: 'hovr:flex', suggestion: 'hover:flex', baseKnown: true },
        ]);
        expect(unknownClasses(theme, ['pressed:tap-target'])).toEqual([]);
    });

    it('preserves the installed compiler prefix when correcting a misspelled utility', async () => {
        const prefixed = join(root, 'prefixed.css');
        writeFileSync(prefixed, '@import "tailwindcss" prefix(ui);');
        expect(await query(prefixed, ['ui:flex', 'ui:hover:flex', 'ui:flex-cols'])).toMatchObject({
            ok: true,
            unknown: [{ token: 'ui:flex-cols', suggestion: 'ui:flex-col', baseKnown: false }],
        });
    });

    it('reports an unresolved import rather than claiming half a stylesheet is a valid design system', async () => {
        const broken = join(root, 'broken.css');
        writeFileSync(broken, '@import "./missing.css";');
        expect(await query(broken, ['flex'])).toMatchObject({ ok: false, reason: expect.stringContaining('missing.css') });
        expect(() => unknownClasses(broken, ['flex'])).toThrow('Cannot verify design-system classes');
        expect(unknownClasses(theme, ['flex'])).toEqual([]);
        expect(await query(theme, ['flex'])).toMatchObject({ ok: true, unknown: [] });
    });

    it('uses the most specific explicitly registered root without matching neighboring path prefixes', () => {
        const child = join(root, 'nested');
        mkdirSync(child);
        const childTheme = join(child, 'theme.css');
        writeFileSync(childTheme, '@theme { --color-child: #fff; }');
        registerProject(child, childTheme);
        expect(themeFileFor(join(child, 'view.html'))).toBe(childTheme);
        expect(colorTokensFor(join(child, 'view.html'))).toEqual(new Set(['child']));
        expect(themeFileFor(join(root, 'nested-other', 'view.html'))).toBe(theme);
        expect(projectFor(`${root}-neighbor/view.html`)).toBeNull();
    });
});
