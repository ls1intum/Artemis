import { describe, expect, it } from 'vitest';
import { parseClassSelectors, parseColorTokens, parseDeclarations, parseImports, parseUtilities, resolveVariables } from './theme.mjs';

describe('theme syntax uses real CSS nodes, not strings resembling declarations', () => {
    it('does not grant utilities from comments or quoted content', () => {
        expect(parseUtilities('/* @utility forged {} */ .sample { content: "@utility also-forged {}"; } @utility genuine { display: flex; }')).toEqual(new Set(['genuine']));
    });

    it('finds escaped selector classes without treating file extensions, attribute values or decimals as classes', () => {
        const css = String.raw`.real, .escaped\:name, [data-title=".not-a-class"] { background: url(./fake.png); opacity: .5; content: ".also-fake"; }`;
        expect(parseClassSelectors(css)).toEqual(new Set(['real', 'escaped:name']));
    });

    it('preserves quoted CSS values and does not interpret their text as theme declarations', () => {
        const css = '@theme { --font-display: "family; --color-fake: red; }"; --color-real: blue; }';
        const result = parseDeclarations(css);
        expect(result.values).toEqual(
            new Map([
                ['font-display', '"family; --color-fake: red; }"'],
                ['color-real', 'blue'],
            ]),
        );
        expect(result.declarations).toHaveLength(2);
        expect(parseColorTokens(css)).toEqual(new Set(['real']));
    });

    it('reads actual imports but not explanatory comments or strings', () => {
        expect(parseImports(`/* @import "./fake.css"; */ .sample { content: '@import "./also-fake.css";'; } @import "./real.css" layer(theme);`)).toEqual(['./real.css']);
    });

    it('reads quoted and unquoted url imports with layer parameters', () => {
        expect(parseImports('@import url(./base.css) layer(theme); @import url("./other.css");')).toEqual(['./base.css', './other.css']);
    });

    it('resolves nested var fallbacks without evaluating quoted strings or URL payloads', () => {
        const values = new Map([
            ['brand', '#123456'],
            ['cycle', 'var(--cycle)'],
        ]);
        expect(resolveVariables('var(--missing, var(--other, var(--brand)))', values)).toBe('#123456');
        expect(resolveVariables('"var(--brand)" url("var(--brand)")', values)).toBe('"var(--brand)" url("var(--brand)")');
        expect(resolveVariables('var(--missing)', values)).toBeNull();
        expect(resolveVariables('var(--cycle)', values)).toBeNull();
    });
});
