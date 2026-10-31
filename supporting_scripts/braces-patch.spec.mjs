import { createRequire } from 'node:module';
import { describe, expect, it } from 'vitest';

// Resolve the copy actually used by stylelint, without depending on pnpm's store layout.
const require = createRequire(import.meta.url);
const requireStylelint = createRequire(require.resolve('stylelint/package.json'));
const requireMicromatch = createRequire(requireStylelint.resolve('micromatch'));
const braces = requireMicromatch('braces');
const micromatch = requireStylelint('micromatch');

describe('braces security patch', () => {
    it.each(['compile', 'expand', 'stringify'])('rejects deeply nested brace patterns before %s traverses them', (method) => {
        // GHSA-vfj7-8cjw-p6xm: 9,001 characters pass the upstream 10,000-character cap.
        const pattern = '{'.repeat(4500) + 'a' + '}'.repeat(4500);
        expect(() => braces[method](pattern)).toThrow(SyntaxError);
        expect(() => braces[method](pattern)).toThrow(/nesting depth/);
    });

    it('also bounds nested parentheses and mixed groups', () => {
        for (const group of ['(', '{(']) {
            expect(() => braces.parse(group.repeat(101) + 'a')).toThrow(/nesting depth/);
        }
    });

    it.each(['compile', 'expand', 'stringify'])('bounds directly supplied ASTs in %s', (method) => {
        const ast = { type: 'root', nodes: [] };
        let node = ast;
        for (let i = 0; i < 4500; i++) {
            const child = { type: 'brace', nodes: [], parent: node };
            node.nodes.push(child);
            node = child;
        }
        node.nodes.push({ type: 'text', value: 'a', parent: node });

        expect(() => braces[method](ast)).toThrow(SyntaxError);
        expect(() => braces[method](ast)).toThrow(/nesting depth/);
    });

    it('preserves ordinary lists, nested alternatives, ranges and escaping', () => {
        expect(braces.expand('src/{main,test}/**/*.{css,scss}')).toEqual(['src/main/**/*.css', 'src/main/**/*.scss', 'src/test/**/*.css', 'src/test/**/*.scss']);
        expect(braces.expand('a/{b,{c,d}}/{01..03}')).toEqual(['a/b/01', 'a/b/02', 'a/b/03', 'a/c/01', 'a/c/02', 'a/c/03', 'a/d/01', 'a/d/02', 'a/d/03']);
        expect(braces.expand(String.raw`a/\{b,c\}`, { keepEscaping: true })).toEqual([String.raw`a/\{b,c\}`]);
        expect(micromatch.braces('{src,.storybook}/**/*.{css,scss}', { expand: true, nodupes: true, keepEscaping: true })).toEqual([
            'src/**/*.css',
            'src/**/*.scss',
            '.storybook/**/*.css',
            '.storybook/**/*.scss',
        ]);
        expect(braces.compile('a/{b,c}')).toBe('a/(b|c)');
    });

    it('accepts 100 levels of valid nesting', () => {
        const pattern = '{'.repeat(100) + 'a' + '}'.repeat(100);
        expect(braces.stringify(pattern)).toBe(pattern);
        expect(braces.expand(pattern)).toEqual([pattern]);
        expect(() => braces.compile(pattern)).not.toThrow();
        expect(() => braces.parse('{' + pattern + '}')).toThrow(/nesting depth/);
    });
});
