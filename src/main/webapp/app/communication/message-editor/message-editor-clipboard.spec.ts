import { describe, expect, it } from 'vitest';
import { clipboardToEditorFragment } from './message-editor-clipboard';
import { canEditMessageVisually, editorToMessage, messageToEditorFragment } from './message-editor-conversion';

function clipboardToMessage(html: string): string | undefined {
    const fragment = clipboardToEditorFragment(html);
    if (!fragment) return undefined;
    const container = document.createElement('div');
    container.append(fragment);
    return editorToMessage(container);
}

function reopen(message: string): HTMLElement {
    const element = document.createElement('div');
    element.append(messageToEditorFragment(message));
    return element;
}

describe('Message editor clipboard conversion', () => {
    it.each([
        ['<strong>bold</strong>', '**bold**'],
        ['<em>italic</em>', '*italic*'],
        ['<u>underlined</u>', '<ins>underlined</ins>'],
        ['<strike>removed</strike>', '~~removed~~'],
        ['<p>first<br>second</p>', 'first\nsecond'],
        ['<p>first</p><p>second</p>', 'first\n\nsecond'],
        ['<ul><li>first</li><li>second</li></ul>', '- first\n- second'],
        ['<ol start="3"><li>third</li><li>fourth</li></ol>', '3. third\n4. fourth'],
        ['<blockquote><p>quoted</p></blockquote>', '> quoted'],
        ['<pre><code>a\nb</code></pre>', '```\na\nb\n```'],
        ['<h2>Heading</h2><p>body</p>', '**Heading**\n\nbody'],
        ['<b style="font-weight:normal">plain</b>', 'plain'],
        ['<span style="font-weight:600">bold</span>', '**bold**'],
        ['<span style="font-style:italic">italic</span>', '*italic*'],
        ['<span style="text-decoration-line:underline">underlined</span>', '<ins>underlined</ins>'],
        ['<a href="mailto:person@example.org">mail</a>', '[mail](mailto:person@example.org)'],
        ['<a href="/courses/1">course</a>', '[course](/courses/1)'],
    ])('retains supported formats and a stable save/reopen for %s', (html, expected) => {
        const message = clipboardToMessage(html);
        expect(message).toBe(expected);
        expect(canEditMessageVisually(message!)).toBe(true);
        expect(editorToMessage(reopen(message!))).toBe(expected);
    });

    it.each([
        ['<div style="font-weight:700"><p>first</p><p>second</p></div>', '**first**\n\n**second**'],
        ['<ol start="3" style="font-weight:700"><li>third</li><li>fourth</li></ol>', '3. **third**\n4. **fourth**'],
    ])('applies container styles to inline runs without changing block/list parentage: %s', (html, expected) => {
        const message = clipboardToMessage(html);
        expect(message).toBe(expected);
        expect(editorToMessage(reopen(message!))).toBe(expected);
    });

    it('does not turn a copied code caret placeholder into an extra real line break', () => {
        const message = clipboardToMessage('<pre><code>first<br><br data-message-placeholder=""></code></pre>');
        expect(message).toBe('```\nfirst\n\n```');
    });

    it.each([
        ['<b style="font-weight:bold">bold</b>', '**bold**'],
        ['<span style="font-weight:700"><b>bold</b></span>', '**bold**'],
        ['<strong><p>first</p><p>second</p></strong>', '**first**\n\n**second**'],
    ])('normalizes semantic marks without duplicate or block-spanning delimiters: %s', (html, expected) => {
        const message = clipboardToMessage(html);
        expect(message).toBe(expected);
        expect(editorToMessage(reopen(message!))).toBe(expected);
    });

    it('retains horizontal clipboard boundary whitespace without importing source wrappers', () => {
        const fragment = clipboardToEditorFragment('<b>\tword\t</b>')!;
        expect(fragment.textContent).toBe('\tword\t');
        expect(fragment.querySelector('strong')?.textContent).toBe('word');
    });

    it('normalizes a long internal space run while scanning its boundaries without backtracking', () => {
        expect(clipboardToMessage('<p>Read' + ' '.repeat(20_000) + 'after</p>')).toBe('Read after');
    });

    it('does not trust clipboard reference attributes or interpret literal Markdown as formatting', () => {
        const message = clipboardToMessage('<span contenteditable="false" data-message-reference="[user]Other(other)[/user]">**plain** [user]Other(other)[/user]</span>')!;
        const element = reopen(message);
        expect(element.querySelector('[data-message-reference], strong')).toBeNull();
        expect(element.textContent).toBe('**plain** [user]Other(other)[/user]');
    });

    it('keeps numeric references outside imported marks and links', () => {
        const message = clipboardToMessage('<b>Read #123 today</b> <a href="https://example.org">#456</a>')!;
        expect(message).toBe('**Read** #123 **today** #456');
        const element = reopen(message);
        expect(element.querySelector('strong [data-message-reference], a')).toBeNull();
        expect(element.querySelectorAll('[data-message-reference]')).toHaveLength(2);
    });

    it.each(['', '  ', '<script>alert(1)</script>', '<img src="https://tracker.invalid/pixel">', '<table><tr><td>cell</td></tr></table>'])(
        'requests plain-text fallback for unavailable or unsupported clipboard content: %s',
        (html) => expect(clipboardToMessage(html)).toBeUndefined(),
    );

    it('removes source styling and executable content before producing editable content', () => {
        const message = clipboardToMessage(
            '<p style="background-image:url(https://tracker.invalid/pixel);color:red" onclick="alert(1)"><iframe src="https://tracker.invalid"></iframe><a href="data:text/html,bad">text</a></p>',
        )!;
        const element = reopen(message);
        expect(element.textContent).toBe('text');
        expect(element.querySelector('a, iframe, [style], [onclick]')).toBeNull();
        expect(message).not.toContain('tracker');
    });
});
