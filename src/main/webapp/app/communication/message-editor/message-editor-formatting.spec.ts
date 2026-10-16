import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { editorToMessage, messageToEditorHtml } from './message-editor-conversion';
import { MessageEditorDom } from './message-editor-dom';

describe('Message editor inline formatting sequences', () => {
    let root: HTMLDivElement;
    let editor: MessageEditorDom;
    const changed = vi.fn();

    function select(node: Node, start: number, end = start): void {
        const range = document.createRange();
        range.setStart(node, start);
        range.setEnd(node, end);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
    }

    function selectAll(): void {
        const range = document.createRange();
        range.selectNodeContents(root);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
    }

    function expectRoundTrip(): HTMLDivElement {
        const restored = document.createElement('div');
        restored.innerHTML = messageToEditorHtml(editorToMessage(root));
        expect(restored.textContent).toBe(root.textContent);
        expect(editorToMessage(restored)).toBe(editorToMessage(root));
        return restored;
    }

    beforeEach(() => {
        changed.mockClear();
        root = document.createElement('div');
        root.contentEditable = 'true';
        document.body.append(root);
        editor = new MessageEditorDom(root, changed);
    });

    afterEach(() => {
        window.getSelection()!.removeAllRanges();
        root.remove();
    });

    it('toggles bold off at a caret without removing the preceding bold text', () => {
        editor.load('**Hello**');
        select(root.querySelector('strong')!.firstChild!, 5);
        editor.format('strong');
        editor.insertText(' world');
        expect(editorToMessage(root)).toBe('**Hello** world');
        expect(root.querySelector('strong')!.textContent).toBe('Hello');
        expect(root.textContent).toBe('Hello world');
        expectRoundTrip();
    });

    it('toggles a pending format on and off before typing without creating empty marks or undo steps', () => {
        editor.load('Hello');
        select(root.querySelector('p')!.firstChild!, 5);
        editor.format('strong');
        editor.format('strong');
        editor.insertText(' world');
        expect(editorToMessage(root)).toBe('Hello world');
        expect(root.querySelector('strong')).toBeNull();
        editor.undo();
        expect(editorToMessage(root)).toBe('Hello');
        editor.redo();
        expect(editorToMessage(root)).toBe('Hello world');
    });

    it.each(['strong', 'em', 'ins', 's', 'code'] as const)('types inside a newly enabled %s mark and can disable it again', (tag) => {
        editor.load('Before ');
        const paragraph = root.querySelector('p')!;
        select(paragraph, paragraph.childNodes.length);
        editor.format(tag);
        editor.insertText('marked');
        expect(root.querySelector(tag)!.textContent).toBe('marked');
        editor.format(tag);
        editor.insertText(' after');
        expect(root.querySelector(tag)!.textContent).toBe('marked');
        expect(root.textContent).toBe('Beforemarked after');
        expectRoundTrip();
    });

    it.each([
        ['strong', 'em'],
        ['em', 'strong'],
        ['strong', 'code'],
        ['em', 'code'],
        ['code', 'strong'],
        ['ins', 's'],
        ['s', 'ins'],
    ] as const)('preserves both %s and %s when applying them successively and reopening', (first, second) => {
        editor.load('word');
        selectAll();
        editor.format(first);
        editor.format(second);
        expect(window.getSelection()!.toString()).toBe('word');
        expect(root.querySelector(first)!.textContent).toBe('word');
        expect(root.querySelector(second)!.textContent).toBe('word');
        const restored = expectRoundTrip();
        expect(restored.querySelector(first)?.textContent).toBe('word');
        expect(restored.querySelector(second)?.textContent).toBe('word');
        editor.format(second);
        expect(root.querySelector(second)).toBeNull();
        expect(root.querySelector(first)!.textContent).toBe('word');
        expect(editorToMessage(root)).not.toContain('undefined');
    });

    it('preserves a paragraph-start caret after formatting and asynchronous reference insertion', () => {
        editor.load('First\n\nSecond');
        const paragraph = root.querySelectorAll('p')[1];
        select(paragraph.firstChild!, 0);
        const bookmark = editor.captureEntitySelection()!;
        select(root.querySelector('p')!.firstChild!, 0, 5);
        editor.format('strong');
        const range = editor.resolveEntitySelection(bookmark)!;
        editor.insertMarkdown('[faq]Question(/courses/1/faq?faqId=1)[/faq] ', range);
        expect(editorToMessage(root)).toBe('**First**\n\n[faq]Question(/courses/1/faq?faqId=1)[/faq] Second');
        expect(root.querySelectorAll('p')[0].textContent).toBe('First');
        expect(root.querySelectorAll('p')[1].textContent).toBe('Question Second');
    });

    it('keeps an asynchronous insertion on an actual empty line after formatting another paragraph', () => {
        editor.load('First\n\nSecond');
        const empty = document.createElement('p');
        empty.append(document.createElement('br'));
        root.insertBefore(empty, root.lastElementChild);
        editor.afterInput();
        select(empty, 0);
        const bookmark = editor.captureEntitySelection()!;
        select(root.querySelector('p')!.firstChild!, 0, 5);
        editor.format('strong');
        editor.insertMarkdown('[faq]Question(/courses/1/faq?faqId=1)[/faq] ', editor.resolveEntitySelection(bookmark)!);
        const paragraphs = root.querySelectorAll('p');
        expect(paragraphs).toHaveLength(3);
        expect(paragraphs[0].textContent).toBe('First');
        expect(paragraphs[1].querySelector('[data-message-reference]')?.textContent).toBe('Question');
        expect(paragraphs[2].textContent).toBe('Second');
        expect(paragraphs[2].querySelector('[data-message-reference]')).toBeNull();
    });

    it('changes an existing link destination without stripping its inline formatting', () => {
        editor.load('Read [**guide**](https://example.org) next');
        const link = root.querySelector('a')!;
        select(link.querySelector('strong')!.firstChild!, 2);
        editor.setLink('guide', 'https://example.org/new', link);
        expect(root.querySelector('a strong')?.textContent).toBe('guide');
        expect(editorToMessage(root)).toBe('Read [**guide**](https://example.org/new) next');
        editor.insertText('!');
        expect(editorToMessage(root)).toBe('Read [**guide**](https://example.org/new)! next');
        expectRoundTrip();
    });

    it('retains uniform link text formatting when renaming a link and continues typing outside it', () => {
        editor.load('[***guide***](https://example.org)');
        const link = root.querySelector('a')!;
        select(link.querySelector('em')!.firstChild!, 1);
        editor.setLink('handbook', 'https://example.org/new', link);
        expect(root.querySelector('a strong em, a em strong')?.textContent).toBe('handbook');
        editor.insertText(' after');
        expect(root.querySelector('a')!.textContent).toBe('handbook');
        expect(root.textContent).toBe('handbook after');
        expectRoundTrip();
    });

    it('unlinks a formatted label without moving subsequent typing before it', () => {
        editor.load('Read [**guide**](https://example.org) next');
        const link = root.querySelector('a')!;
        select(link.querySelector('strong')!.firstChild!, 2);
        editor.unlink(link);
        editor.insertText('!');
        expect(root.querySelector('a')).toBeNull();
        expect(root.querySelector('strong')!.textContent).toBe('guide');
        expect(editorToMessage(root)).toBe('Read **guide**! next');
    });

    it('retains the selected underline through replacement after overlapping format changes', () => {
        editor.load('**alpha beta gamma**');
        select(root.querySelector('strong')!.firstChild!, 6, 10);
        editor.format('strong');
        editor.format('ins');
        editor.format('s');
        editor.format('s');
        expect(window.getSelection()!.toString()).toBe('beta');
        expect(editor.needsManagedTextInsertion()).toBe(true);
        editor.insertText('BETA');
        expect(root.querySelector('ins')?.textContent).toBe('BETA');
        expect(editorToMessage(root)).toBe('**alpha** <ins>BETA</ins> **gamma**');
        editor.undo();
        expect(window.getSelection()!.toString()).toBe('beta');
        editor.redo();
        expect(root.querySelector('ins')?.textContent).toBe('BETA');
        expectRoundTrip();
    });

    it('does not apply an unfinished composition to a subsequently loaded message', () => {
        editor.load('Hello');
        select(root.querySelector('p')!.firstChild!, 5);
        editor.format('strong');
        editor.beginComposition();
        editor.load('A different message');
        select(root.querySelector('p')!.firstChild!, 19);
        editor.endComposition();
        expect(editorToMessage(root)).toBe('A different message');
        expect(root.querySelector('strong')).toBeNull();
    });

    it('keeps a repeated selected format cycle local to the chosen word', () => {
        editor.load('Before **chosen** after');
        select(root.querySelector('strong')!.firstChild!, 0, 6);
        for (const tag of ['em', 'ins', 's', 'code', 'strong', 'em', 'ins', 's', 'code'] as const) {
            editor.format(tag);
            expect(root.textContent).toBe('Before chosen after');
            expect(window.getSelection()!.toString()).toBe('chosen');
        }
        expect(editorToMessage(root)).toBe('Before chosen after');
        expect(root.querySelector('strong, em, ins, s, code')).toBeNull();
        editor.insertText('replacement');
        expect(editorToMessage(root)).toBe('Before replacement after');
    });
});
