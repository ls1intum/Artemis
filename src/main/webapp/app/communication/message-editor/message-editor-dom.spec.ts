import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MessageEditorDom } from './message-editor-dom';

describe('Message editor editing transactions', () => {
    let root: HTMLDivElement;
    let editor: MessageEditorDom;
    const changed = vi.fn();

    function select(node: Node, start: number, end = start) {
        const range = document.createRange();
        range.setStart(node, start);
        range.setEnd(node, end);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
    }

    beforeEach(() => {
        changed.mockClear();
        root = document.createElement('div');
        document.body.append(root);
        editor = new MessageEditorDom(root, changed);
    });
    afterEach(() => root.remove());

    it('formats the selected text and undoes/redoes the transaction', () => {
        editor.load('Hello world');
        select(root.querySelector('p')!.firstChild!, 6, 11);
        editor.format('strong');
        expect(changed).toHaveBeenLastCalledWith('Hello **world**');
        editor.undo();
        expect(changed).toHaveBeenLastCalledWith('Hello world');
        editor.redo();
        expect(changed).toHaveBeenLastCalledWith('Hello **world**');
    });

    it('replaces only the active completion range, including repeated queries', () => {
        editor.load('@ann and @ann');
        select(root.querySelector('p')!.firstChild!, 13);
        const completion = editor.completion()!;
        expect(completion.query).toBe('ann');
        editor.insertMarkdown('[user]Anna(anna)[/user] ', completion.range);
        expect(changed).toHaveBeenLastCalledWith('@ann and [user]Anna(anna)[/user]');
        expect(root.querySelector('p')!.textContent).toBe('@ann and @Anna ');
        expect(window.getSelection()!.anchorNode?.textContent).toBe(' ');
        editor.undo();
        expect(changed).toHaveBeenLastCalledWith('@ann and @ann');
    });

    it.each(['Email anna@example.org', 'C#', 'https://example.org/#anchor', '`@anna`'])('does not offer completion in %s', (text) => {
        editor.load(text);
        const node = root.querySelector('code, a')?.firstChild ?? root.querySelector('p')!.lastChild!;
        select(node, node.textContent!.length);
        expect(editor.completion()).toBeUndefined();
    });

    it('selects a complete mention before deleting it and restores it on undo', () => {
        editor.load('Hello [user]Anna(anna)[/user]');
        const paragraph = root.querySelector('p')!;
        select(paragraph, paragraph.childNodes.length);
        expect(editor.deleteItem(true)).toBe(true);
        expect(window.getSelection()!.toString()).toBe('@Anna');
        expect(changed).not.toHaveBeenCalled();
        expect(editor.deleteItem(true)).toBe(true);
        expect(changed).toHaveBeenLastCalledWith('Hello');
        editor.undo();
        expect(changed).toHaveBeenLastCalledWith('Hello [user]Anna(anna)[/user]');
    });

    it('supports forward deletion and whole-link selection', () => {
        editor.load('[Guide](https://example.org) after');
        select(root.querySelector('p')!, 0);
        expect(editor.deleteItem(false)).toBe(true);
        expect(window.getSelection()!.toString()).toBe('Guide');
        editor.deleteItem(false);
        expect(changed).toHaveBeenLastCalledWith('after');
    });

    it('edits a link target without exposing Markdown, and can unlink while preserving its label', () => {
        editor.load('Read the guide');
        select(root.querySelector('p')!.firstChild!, 9, 14);
        editor.setLink('guide', 'https://example.org');
        expect(changed).toHaveBeenLastCalledWith('Read the [guide](https://example.org)');
        const link = root.querySelector('a')!;
        editor.setLink('manual', 'https://example.org/manual', link);
        expect(changed).toHaveBeenLastCalledWith('Read the [manual](https://example.org/manual)');
        editor.unlink(root.querySelector('a')!);
        expect(changed).toHaveBeenLastCalledWith('Read the manual');
    });

    it('keeps code and native text input in the same undo history', () => {
        editor.load('text');
        select(root.querySelector('p')!.firstChild!, 4);
        editor.beforeInput();
        root.querySelector('p')!.firstChild!.textContent = 'text typed';
        select(root.querySelector('p')!.firstChild!, 10);
        editor.afterInput();
        editor.format('code');
        editor.insertText('x');
        editor.undo();
        editor.undo();
        editor.undo();
        expect(changed).toHaveBeenLastCalledWith('text');
    });

    it('does not interpret pasted plain text as markup or HTML', () => {
        editor.load('');
        editor.insertText('<img onerror=alert(1)> **plain**');
        expect(root.querySelector('img')).toBeNull();
        expect(root.querySelector('strong')).toBeNull();
        expect(root.textContent).toContain('**plain**');
    });

    it('preserves line breaks when pasting plain text', () => {
        editor.load('');
        editor.insertText('First\nSecond');
        expect(changed).toHaveBeenLastCalledWith('First\nSecond');
    });

    it('formats a paragraph as a list, quote, or code block', () => {
        editor.load('First');
        select(root.querySelector('p')!.firstChild!, 2);
        editor.block('ul');
        expect(changed).toHaveBeenLastCalledWith('- First');
        editor.undo();
        editor.block('blockquote');
        expect(changed).toHaveBeenLastCalledWith('> First');
        editor.undo();
        editor.block('pre');
        expect(changed).toHaveBeenLastCalledWith('```\nFirst\n```');
    });

    it('preserves actual newlines inserted into a code block', () => {
        editor.load('```\nfirst\n```');
        const code = root.querySelector('code')!;
        select(code.firstChild!, 5);
        editor.insertText('\nsecond');
        expect(changed).toHaveBeenLastCalledWith('```\nfirst\nsecond\n```');
    });

    it('provides a visible caret position after a trailing newline in code', () => {
        editor.load('first');
        select(root.querySelector('p')!.firstChild!, 5);
        editor.block('pre');
        editor.insertText('\n');
        expect(root.querySelector('pre code br[data-message-placeholder]')).not.toBeNull();
        expect(window.getSelection()!.anchorNode?.nodeName).toBe('CODE');
        editor.insertText('second');
        expect(changed).toHaveBeenLastCalledWith('```\nfirst\nsecond\n```');
    });

    it('preserves a separating space when typing after an inserted mention', () => {
        editor.load('');
        editor.insertMarkdown('[user]Anna(anna)[/user] ');
        editor.insertText('hello');
        expect(changed).toHaveBeenLastCalledWith('[user]Anna(anna)[/user] hello');
    });

    it('formats a new native text run as a block instead of leaving its text behind', () => {
        editor.load('');
        root.textContent = 'first';
        select(root.firstChild!, 5);
        editor.afterInput();
        editor.block('pre');
        editor.insertText('\nsecond');
        expect(changed).toHaveBeenLastCalledWith('```\nfirst\nsecond\n```');
    });

    it('toggles formatting off without discarding selected text', () => {
        editor.load('**bold**');
        select(root.querySelector('strong')!.firstChild!, 0, 4);
        editor.format('strong');
        expect(changed).toHaveBeenLastCalledWith('bold');
        editor.undo();
        expect(changed).toHaveBeenLastCalledWith('**bold**');
    });

    it.each(['ul', 'ol', 'blockquote'] as const)('toggles an existing %s block back to paragraphs', (tag) => {
        editor.load('First');
        select(root.querySelector('p')!.firstChild!, 2);
        editor.block(tag);
        editor.block(tag);
        expect(changed).toHaveBeenLastCalledWith('First');
    });

    it('preserves code line breaks when removing code-block formatting', () => {
        editor.load('```\nfirst\nsecond\n```');
        select(root.querySelector('code')!.firstChild!, 2);
        editor.block('pre');
        expect(changed).toHaveBeenLastCalledWith('first\nsecond');
    });

    it('formats across paragraphs without wrapping block elements in inline marks', () => {
        editor.load('First paragraph\n\nSecond paragraph');
        const paragraphs = root.querySelectorAll('p');
        const range = document.createRange();
        range.setStart(paragraphs[0].firstChild!, 6);
        range.setEnd(paragraphs[1].firstChild!, 6);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        editor.format('strong');
        expect(changed).toHaveBeenLastCalledWith('First **paragraph**\n\n**Second** paragraph');
        expect(root.querySelector('strong p')).toBeNull();
    });

    it('removes formatting only from selected text and preserves the selection when typing a replacement', () => {
        editor.load('**Hello world**');
        select(root.querySelector('strong')!.firstChild!, 6, 11);
        editor.format('strong');
        expect(changed).toHaveBeenLastCalledWith('**Hello** world');
        expect(window.getSelection()!.toString()).toBe('world');
        editor.insertText('earth');
        expect(changed).toHaveBeenLastCalledWith('**Hello** earth');
    });

    it('preserves other inline marks, link targets, and reference payloads when changing formatting', () => {
        editor.load('*Hello* [world](https://example.org) [user]Anna(anna)[/user]');
        const range = document.createRange();
        range.selectNodeContents(root);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        editor.format('strong');
        expect(root.querySelector('strong em')).not.toBeNull();
        expect(root.querySelector('a')!.getAttribute('href')).toBe('https://example.org');
        expect(root.querySelector('[data-message-reference]')!.getAttribute('data-message-reference')).toBe('[user]Anna(anna)[/user]');
    });

    it('leaves ordinary character deletion and unsafe links to their respective handlers', () => {
        editor.load('Text');
        select(root.querySelector('p')!.firstChild!, 2);
        expect(editor.deleteItem(true)).toBe(false);
        expect(editor.deleteItem(false)).toBe(false);
        editor.setLink('Bad', 'javascript:alert(1)');
        expect(changed).not.toHaveBeenCalled();
    });
});
