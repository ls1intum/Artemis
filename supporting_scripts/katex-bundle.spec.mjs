import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { build } from 'esbuild';
import { realpathSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';

describe('KaTeX formula conversion after production chunk optimization', () => {
    let dom;
    let htmlForMarkdown;

    beforeAll(async () => {
        const require = createRequire(import.meta.url);
        // Use Angular's optimizer, not Vite's separate Rolldown dependency used by Vitest.
        const angularRequire = createRequire(realpathSync(require.resolve('@angular/build/package.json')));
        const { rolldown } = await import(angularRequire.resolve('rolldown'));
        const result = await build({
            entryPoints: [fileURLToPath(new URL('../src/main/webapp/app/foundation/util/markdown.conversion.util.ts', import.meta.url))],
            bundle: true,
            write: false,
            minify: true,
            format: 'esm',
            target: 'es2022',
        });
        const bundle = await rolldown({
            input: 'markdown.js',
            plugins: [{ name: 'markdown-bundle', resolveId: (id) => id, load: () => result.outputFiles[0].text }],
        });
        try {
            // Match Angular's second-stage optimization: lone surrogate escapes must survive it.
            const { output } = await bundle.generate({ format: 'iife', name: 'markdown', minify: { mangle: false, compress: false } });
            dom = new JSDOM('<!doctype html>', { runScripts: 'outside-only', url: 'http://localhost/' });
            htmlForMarkdown = dom.window.eval(`${output.find((file) => file.type === 'chunk').code}\nmarkdown.htmlForMarkdown;`);
        } finally {
            await bundle.close();
        }
    });

    afterAll(() => dom?.window.close());

    it.each([
        ['$\\frac{1}{2}$', 'mfrac', '12'],
        ['$$\\frac{1}{2}$$', 'mfrac', '12'],
        ['$\\sqrt{x}$', 'msqrt', 'x'],
        ['$\\alpha + \\beta$', 'mrow', 'α+β'],
    ])('renders %s as math rather than a lexer error', (markdown, mathElement, expectedText) => {
        const container = dom.window.document.createElement('div');
        container.innerHTML = htmlForMarkdown(markdown);

        expect(container.querySelector('.katex-error')?.textContent).toBeUndefined();
        expect(container.querySelector('.katex-html')).not.toBeNull();
        expect(container.querySelector(`math ${mathElement}`)?.textContent).toBe(expectedText);
    });
});
