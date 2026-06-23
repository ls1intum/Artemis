import { describe, expect, it } from 'vitest';
import { renderMarkdownToHtml, renderPostingMarkdownToHtml } from 'app/foundation/util/markdown-render.util';

describe('markdown-render.util', () => {
    describe('renderMarkdownToHtml', () => {
        it('returns an empty string for empty or undefined input', async () => {
            expect(await renderMarkdownToHtml('')).toBe('');
            expect(await renderMarkdownToHtml(undefined)).toBe('');
        });

        it('renders markdown to sanitized HTML', async () => {
            const html = await renderMarkdownToHtml('# Title\n\n**bold**');
            expect(html).toContain('<h1');
            expect(html).toContain('<strong>bold</strong>');
        });

        it('honors the lineBreaks flag', async () => {
            const html = await renderMarkdownToHtml('a\nb', [], undefined, undefined, true);
            expect(html).toContain('<br');
        });
    });

    describe('renderPostingMarkdownToHtml', () => {
        it('returns an empty string for empty input', async () => {
            expect(await renderPostingMarkdownToHtml('')).toBe('');
        });

        it('marks the last paragraph before a reference as inline', async () => {
            const html = await renderPostingMarkdownToHtml('first\n\nlast', true);
            expect(html).toContain('inline-paragraph">last');
            expect(html.startsWith('<p class="inline-paragraph">')).toBe(false);
        });

        it('marks the first paragraph after a reference as inline', async () => {
            const html = await renderPostingMarkdownToHtml('first\n\nlast', false);
            expect(html).toContain('inline-paragraph">first');
        });
    });
});
