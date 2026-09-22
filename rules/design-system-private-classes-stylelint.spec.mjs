import { describe, expect, it } from 'vitest';
import stylelint from 'stylelint';
import rule from './design-system-private-classes-stylelint.mjs';

async function lint(code, prefix = 'tum-ui-') {
    const result = await stylelint.lint({
        code,
        customSyntax: 'postcss-scss',
        config: {
            plugins: [rule],
            rules: { 'design-system/no-private-classes': prefix },
        },
    });
    expect(result.results[0].invalidOptionWarnings).toEqual([]);
    return result.results[0].warnings;
}

describe('private design-system CSS namespaces', () => {
    it.each([
        '.tum-ui-panel-header { color: red; }',
        '.tum-ui-#{$part} { color: red; }',
        String.raw`.tum\2d ui-panel-header { color: red; }`,
        String.raw`:not(.tum\2d ui-panel-header) { color: red; }`,
        '.page:has(.tum-ui-panel-header) { color: red; }',
        String.raw`[class~="tum\2d ui-btn"] { color: red; }`,
        '[class*="tum-ui-"] { color: red; }',
        '[class="layout tum-ui-btn"] { color: red; }',
        '[CLASS~="TUM-UI-BTN" i] { color: red; }',
        '.tum { &-ui-panel-header { color: red; } }',
        '.page { @extend .tum-ui-btn !optional; }',
        '@at-root .tum-ui-panel-header { color: red; }',
    ])('rejects decoded private selectors, including exclusions and Sass composition: %s', async (code) => {
        expect(await lint(code)).toEqual([expect.objectContaining({ rule: 'design-system/no-private-classes', severity: 'error' })]);
    });

    it.each([
        '.not-a-tum-ui-panel { color: red; }',
        '.tum-uiish-panel { color: red; }',
        '[class="layout not-tum-ui-panel"] { color: red; }',
        '[class~="TUM-UI-BTN"] { color: red; }',
        '[data-label=".tum-ui-panel"] { color: red; }',
        '[data-class="tum-ui-panel"] { color: red; }',
        'tum-ui-panel { margin: 1rem; }',
        '.page { content: ".tum-ui-panel-header"; }',
    ])('allows unrelated classes, element selectors, data and declaration strings: %s', async (code) => {
        expect(await lint(code)).toEqual([]);
    });

    it('reports the authored dependency once instead of repeating inherited private ancestors', async () => {
        const result = await lint('.tum-ui-panel-header {\n .label { color: red; }\n}');
        expect(result).toHaveLength(1);
        expect(result[0]).toMatchObject({ line: 1, column: 1 });
        expect(await lint('.page, .other { .tum-ui-panel-header { color: red; } }')).toHaveLength(1);
    });

    it('takes an explicit namespace instead of embedding TUM UI component names', async () => {
        expect(await lint('.other-private-button { color: red; }', 'other-private-')).toHaveLength(1);
        expect(await lint('.tum-ui-btn { color: red; }', 'other-private-')).toEqual([]);
    });
});
