import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { DiffMatchPatch } from 'diff-match-patch-typescript';
import { captureMessageBookmark, messageBookmarkText, restoreMessageBookmark } from './message-editor-bookmarks';

describe('Message editor asynchronous bookmarks', () => {
    let root: HTMLDivElement;

    function rangeAt(node: Node, offset: number): Range {
        const range = document.createRange();
        range.setStart(node, offset);
        range.collapse(true);
        return range;
    }

    function insertAt(range: Range, text: string): void {
        range.deleteContents();
        range.insertNode(document.createTextNode(text));
    }

    beforeEach(() => {
        root = document.createElement('div');
        document.body.append(root);
    });

    afterEach(() => root.remove());

    it('distinguishes an empty paragraph from both neighboring paragraphs after an inline rebuild', () => {
        root.innerHTML = '<p>First</p><p><br></p><p>Second</p>';
        const bookmark = captureMessageBookmark(root, rangeAt(root.children[1], 0));
        root.innerHTML = '<p><strong>First</strong></p><p><br></p><p>Second</p>';

        insertAt(restoreMessageBookmark(root, bookmark.start, bookmark.end), 'HERE');

        expect(root.children[0].textContent).toBe('First');
        expect(root.children[1].textContent).toBe('HERE');
        expect(root.children[2].textContent).toBe('Second');
    });

    it('assigns distinct positions to consecutive empty paragraphs without BR children', () => {
        root.innerHTML = '<p>First</p><p></p><p></p><p>Last</p>';
        const first = captureMessageBookmark(root, rangeAt(root.children[1], 0));
        const second = captureMessageBookmark(root, rangeAt(root.children[2], 0));

        expect(second.start).toBeGreaterThan(first.start);
        root.innerHTML = '<p><em>First</em></p><p></p><p></p><p>Last</p>';
        insertAt(restoreMessageBookmark(root, second.start, second.end), 'second empty');

        expect(root.children[1].textContent).toBe('');
        expect(root.children[2].textContent).toBe('second empty');
        expect(root.children[3].textContent).toBe('Last');
    });

    it('keeps the two sides of a soft line break distinct', () => {
        root.innerHTML = '<p>first<br>second</p>';
        const paragraph = root.firstElementChild!;
        const before = captureMessageBookmark(root, rangeAt(paragraph, 1));
        const after = captureMessageBookmark(root, rangeAt(paragraph, 2));

        expect(after.start).toBe(before.start + 1);
        root.innerHTML = '<p><strong>first</strong><br><em>second</em></p>';
        insertAt(restoreMessageBookmark(root, after.start, after.end), 'NEXT');

        expect(root.querySelector('br')!.previousSibling!.textContent).toBe('first');
        expect(root.querySelector('br')!.nextSibling!.textContent).toBe('NEXT');
        expect(root.textContent).toBe('firstNEXTsecond');
    });

    it('excludes invisible caret placeholders while retaining real trailing breaks', () => {
        root.innerHTML = '<p>first<br><br data-message-placeholder></p>';
        const withPlaceholder = messageBookmarkText(root);
        root.querySelector('[data-message-placeholder]')!.remove();

        expect(messageBookmarkText(root)).toBe(withPlaceholder);
        expect(withPlaceholder).toBe('first\n\u2029');
    });

    it('restores the trailing-line caret before its invisible placeholder', () => {
        root.innerHTML = '<p>first<br><br data-message-placeholder></p>';
        const bookmark = captureMessageBookmark(root, rangeAt(root.firstElementChild!, 2));
        root.innerHTML = '<p><strong>first</strong><br><br data-message-placeholder></p>';

        insertAt(restoreMessageBookmark(root, bookmark.start, bookmark.end), 'second');

        expect(root.querySelector('br[data-message-placeholder]')!.previousSibling!.textContent).toBe('second');
        expect(root.querySelector('br:not([data-message-placeholder])')!.nextSibling!.textContent).toBe('second');
    });

    it('restores an empty paragraph before a placeholder instead of inserting after it', () => {
        root.innerHTML = '<p>First</p><p><br data-message-placeholder></p><p>Second</p>';
        const bookmark = captureMessageBookmark(root, rangeAt(root.children[1], 0));

        insertAt(restoreMessageBookmark(root, bookmark.start, bookmark.end), 'HERE');

        expect(root.children[1].firstChild!.textContent).toBe('HERE');
        expect(root.children[1].lastChild).toBe(root.querySelector('[data-message-placeholder]'));
    });

    it('keeps bookmark text stable when a paragraph becomes a list item or quote paragraph', () => {
        root.innerHTML = '<p>First</p><p><br></p><p>Second</p>';
        const text = messageBookmarkText(root);
        root.innerHTML = '<ul><li><strong>First</strong></li></ul><p><br></p><blockquote><p>Second</p></blockquote>';

        expect(messageBookmarkText(root)).toBe(text);
        expect(text).toBe('First\u2029\n\u2029Second\u2029');
    });

    it('maps an empty-paragraph bookmark through text inserted before it', () => {
        root.innerHTML = '<p>First</p><p><br></p><p>Second</p>';
        const bookmark = captureMessageBookmark(root, rangeAt(root.children[1], 0));
        root.innerHTML = '<p>First changed</p><p><br></p><p>Second</p>';
        const diff = new DiffMatchPatch();
        const changes = diff.diff_main(bookmark.text, messageBookmarkText(root));
        const range = restoreMessageBookmark(root, diff.diff_xIndex(changes, bookmark.start), diff.diff_xIndex(changes, bookmark.end));

        insertAt(range, 'HERE');

        expect(root.children[0].textContent).toBe('First changed');
        expect(root.children[1].textContent).toBe('HERE');
        expect(root.children[2].textContent).toBe('Second');
    });

    it('keeps nested list content and its following empty item independently addressable', () => {
        root.innerHTML = '<ul><li>First<ul><li>Nested</li></ul></li><li><br></li><li>Last</li></ul>';
        const bookmark = captureMessageBookmark(root, rangeAt(root.querySelector('ul')!.children[1], 0));
        const text = messageBookmarkText(root);
        root.innerHTML = '<ul><li><p><strong>First</strong></p><ul><li><p>Nested</p></li></ul></li><li><p><br></p></li><li><p>Last</p></li></ul>';

        expect(messageBookmarkText(root)).toBe(text);
        insertAt(restoreMessageBookmark(root, bookmark.start, bookmark.end), 'HERE');

        expect(root.querySelector('ul')!.children[1].textContent).toBe('HERE');
        expect(root.querySelector('ul ul li')!.textContent).toBe('Nested');
        expect(root.querySelector('ul')!.lastElementChild!.textContent).toBe('Last');
    });

    it('retains a caret in a native bare text run when it gains a paragraph wrapper', () => {
        root.innerHTML = 'First<p>Second</p>Last';
        const bookmark = captureMessageBookmark(root, rangeAt(root.lastChild!, 2));
        root.innerHTML = '<p>First</p><p>Second</p><p><em>Last</em></p>';

        expect(messageBookmarkText(root)).toBe(bookmark.text);
        insertAt(restoreMessageBookmark(root, bookmark.start, bookmark.end), '!');

        expect(root.children[2].textContent).toBe('La!st');
    });

    it('restores a selection spanning nested marks and a protected reference after rebuilding inline runs', () => {
        root.innerHTML = '<p>Before <strong>marked <em>words</em></strong> <span contenteditable="false" data-message-reference="source">@Anna</span> after</p>';
        const range = document.createRange();
        range.setStart(root.querySelector('em')!.firstChild!, 1);
        range.setEnd(root.querySelector('p')!.lastChild!, 3);
        const bookmark = captureMessageBookmark(root, range);
        root.innerHTML = '<p>Before marked <ins>words</ins> <span contenteditable="false" data-message-reference="source">@Anna</span> after</p>';

        const restored = restoreMessageBookmark(root, bookmark.start, bookmark.end);

        expect(restored.toString()).toBe('ords @Anna af');
        expect(restored.startContainer.textContent).toBe('words');
        expect(restored.startOffset).toBe(1);
        expect(root.querySelector('[data-message-reference]')!.getAttribute('data-message-reference')).toBe('source');
    });

    it('restores a caret after a reference outside its protected contents', () => {
        root.innerHTML = '<p><span contenteditable="false" data-message-reference="source">@Anna</span></p>';
        const bookmark = captureMessageBookmark(root, rangeAt(root.firstElementChild!, 1));
        root.innerHTML = '<p><strong><span contenteditable="false" data-message-reference="source">@Anna</span></strong></p>';

        insertAt(restoreMessageBookmark(root, bookmark.start, bookmark.end), ' after');

        expect(root.querySelector('[data-message-reference]')!.textContent).toBe('@Anna');
        expect(root.textContent).toBe('@Anna after');
    });

    it.each(['code', ''])('restores a caret immediately before a code block containing %j outside that block', (content) => {
        root.innerHTML = `<p>Before</p><pre><code>${content}</code></pre><p>After</p>`;
        const bookmark = captureMessageBookmark(root, rangeAt(root, 1));
        root.innerHTML = `<p><strong>Before</strong></p><pre><code>${content}</code></pre><p>After</p>`;

        const restored = restoreMessageBookmark(root, bookmark.start, bookmark.end);
        insertAt(restored, 'HERE');

        const codeBlock = root.querySelector('pre')!;
        expect(codeBlock.textContent).toBe(content);
        expect(codeBlock.previousSibling!.textContent).toBe('HERE');
        expect(root.firstElementChild!.textContent).toBe('Before');
        expect(root.lastElementChild!.textContent).toBe('After');
    });

    it('clamps a removed selection end to the end of the remaining document', () => {
        root.innerHTML = '<p>Before</p><p>After</p>';
        const range = document.createRange();
        range.setStart(root.children[0].firstChild!, 2);
        range.setEnd(root.children[1].firstChild!, 5);
        const bookmark = captureMessageBookmark(root, range);
        root.innerHTML = '<p>Before</p>';

        const restored = restoreMessageBookmark(root, bookmark.start, bookmark.end);

        expect(restored.toString()).toBe('fore');
        insertAt(restored, ' replacement');
        expect(root.textContent).toBe('Be replacement');
    });

    it('clamps negative positions and restores a collapsed caret in an empty editor', () => {
        const range = restoreMessageBookmark(root, -100, 100);

        expect(range.collapsed).toBe(true);
        insertAt(range, 'first');
        expect(root.textContent).toBe('first');
    });
});
