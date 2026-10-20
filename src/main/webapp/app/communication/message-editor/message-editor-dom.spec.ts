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

    it('converts a typed list marker on a soft line and keeps undo, redo and following text', () => {
        editor.load('Introduction');
        select(root.querySelector('p')!.firstChild!, 12);
        editor.insertText('\n-');
        expect(editor.convertTypedListMarker(' ')).toBe(true);
        editor.insertText('Item');
        expect(root.querySelector('ul > li')?.textContent).toBe('Item');
        expect(changed).toHaveBeenLastCalledWith('Introduction\n\n- Item');
        editor.undo();
        editor.undo();
        expect(root.querySelector('ul')).toBeNull();
        expect(root.textContent).toBe('Introduction-');
        editor.redo();
        editor.redo();
        expect(root.querySelector('ul > li')?.textContent).toBe('Item');
    });

    it.each(['`-`', '```\n-\n```', '> \\-'])('keeps a marker literal inside %s', (message) => {
        editor.load(message);
        const text = root.querySelector('code, p')!.firstChild!;
        select(text, text.textContent!.length);
        changed.mockClear();

        expect(editor.convertTypedListMarker(' ')).toBe(false);
        expect(changed).not.toHaveBeenCalled();
    });

    it('does not turn a protected reference labelled as a marker into a list', () => {
        editor.load('[user]-(anna)[/user]');
        const reference = root.querySelector('[data-message-reference]')!;
        const range = document.createRange();
        range.setStartAfter(reference);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        changed.mockClear();

        expect(editor.convertTypedListMarker(' ')).toBe(false);
        expect(root.querySelector('[data-message-reference]')).toBe(reference);
        expect(changed).not.toHaveBeenCalled();
    });

    it('keeps pasted markers and mid-line punctuation literal', () => {
        editor.load('');
        editor.insertText('- pasted');
        expect(root.querySelector('ul')).toBeNull();
        expect(changed).toHaveBeenLastCalledWith('\\- pasted');
        editor.insertText(' -');
        expect(editor.convertTypedListMarker(' ')).toBe(false);
        expect(root.querySelector('ul')).toBeNull();
    });

    it.each(['code', 'quote'])('writes normal text around a %s block and preserves undo', (kind) => {
        editor.load(kind === 'code' ? '```\ninside\n```' : '> inside');
        const block = root.querySelector(kind === 'code' ? 'pre' : 'blockquote')!;
        const range = document.createRange();
        range.selectNodeContents(block);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        expect(editor.leaveBlock('ArrowUp')).toBe(true);
        editor.insertText('Before');
        range.selectNodeContents(block);
        range.collapse(false);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        editor.insertText('\n');
        expect(editor.leaveBlock('Enter')).toBe(true);
        editor.insertText('After');

        expect(block.textContent).toBe('inside');
        expect(root.firstElementChild?.textContent).toBe('Before');
        expect(root.lastElementChild?.textContent).toBe('After');
        editor.undo();
        editor.undo();
        expect(root.lastElementChild?.matches(kind === 'code' ? 'pre' : 'blockquote')).toBe(true);
        editor.redo();
        editor.redo();
        expect(root.lastElementChild?.textContent).toBe('After');
    });

    it('does not exit a formatted block from its middle or with a range selected', () => {
        editor.load('```\ninside\n```');
        const text = root.querySelector('code')!.firstChild!;
        select(text, 2);
        expect(editor.leaveBlock('ArrowUp')).toBe(false);
        expect(editor.leaveBlock('ArrowDown')).toBe(false);
        expect(editor.leaveBlock('Enter')).toBe(false);
        select(text, 0, 6);
        expect(editor.leaveBlock('ArrowDown')).toBe(false);
    });

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

    it('replaces a colon emoji query without consuming the surrounding text and can undo it', () => {
        editor.load('Keep :smile suffix');
        select(root.querySelector('p')!.firstChild!, 11);
        const completion = editor.completion()!;
        expect(completion.trigger).toBe(':');
        expect(completion.query).toBe('smile');
        expect(completion.range.toString()).toBe(':smile');
        editor.insertMarkdown('😄 ', completion.range);
        expect(root.textContent).toBe('Keep 😄  suffix');
        editor.undo();
        expect(root.textContent).toBe('Keep :smile suffix');
        const prefix = document.createRange();
        prefix.selectNodeContents(root);
        prefix.setEnd(window.getSelection()!.anchorNode!, window.getSelection()!.anchorOffset);
        expect(prefix.toString()).toBe('Keep :smile');
    });

    it.each(['At 10:30', 'https:', ':', ':D', ':-D', ':p', ':)', ':-)', '` :smile`'])('does not intercept typing or Enter for %s', (message) => {
        editor.load(message);
        const node = root.querySelector('code')?.firstChild ?? root.querySelector('p')!.lastChild!;
        select(node, node.textContent!.length);
        expect(editor.completion()).toBeUndefined();
    });

    it('selects a complete mention before deleting it and restores it on undo', () => {
        editor.load('Hello [user]Anna(anna)[/user]');
        const paragraph = root.querySelector('p')!;
        select(paragraph, paragraph.childNodes.length);
        expect(editor.deleteItem(true)).toBe(true);
        expect(window.getSelection()!.toString()).toBe('@Anna');
        expect(root.querySelector('[data-message-selected]')?.textContent).toBe('@Anna');
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

    it('selects an inserted reference on the first Backspace across its completion space, then removes it on the second', () => {
        editor.load('Before ');
        select(root.querySelector('p')!.firstChild!, 6);
        editor.insertMarkdown('[user]Anna(anna)[/user] ');
        changed.mockClear();
        expect(editor.deleteItem(true)).toBe(true);
        expect(changed).not.toHaveBeenCalled();
        expect(window.getSelection()!.toString()).toBe('@Anna');
        expect(root.querySelector('[data-message-selected]')?.textContent).toBe('@Anna');
        expect(editor.deleteItem(true)).toBe(true);
        expect(root.textContent).toBe('Before');
        expect(root.querySelector('[data-message-reference]')).toBeNull();
        editor.undo();
        expect(root.querySelector('[data-message-reference]')?.textContent).toBe('@Anna');
    });

    it('preserves text on both sides when deleting a link from after its separating space', () => {
        editor.load('Read [Guide](https://example.org) next');
        const paragraph = root.querySelector('p')!;
        select(paragraph.lastChild!, 1);
        expect(editor.deleteItem(true)).toBe(true);
        expect(window.getSelection()!.toString()).toBe('Guide');
        expect(root.querySelector('[data-message-selected]')?.tagName).toBe('A');
        expect(editor.deleteItem(true)).toBe(true);
        expect(root.textContent).toBe('Read next');
        expect(changed).toHaveBeenLastCalledWith('Read next');
    });

    it('clears the item highlight when the caret moves away', () => {
        editor.load('Read [Guide](https://example.org)');
        const paragraph = root.querySelector('p')!;
        select(paragraph, paragraph.childNodes.length);
        editor.deleteItem(true);
        select(paragraph.firstChild!, 2);
        editor.rememberSelection();
        expect(root.querySelector('[data-message-selected]')).toBeNull();
        expect(editor.deleteItem(true)).toBe(false);
    });

    it('recovers a resource insertion bookmark after undo replaces the editable DOM', () => {
        editor.load('Before');
        select(root.querySelector('p')!.firstChild!, 6);
        editor.insertText(' after');
        const bookmark = editor.captureEntitySelection()!;
        editor.undo();
        const recovered = editor.resolveEntitySelection(bookmark)!;
        editor.insertMarkdown('[faq]Question(/courses/1/faq?faqId=1)[/faq]', recovered);
        expect(changed).toHaveBeenLastCalledWith('Before[faq]Question(/courses/1/faq?faqId=1)[/faq]');
    });

    it('keeps a recovered caret after an existing reference rather than selecting that reference', () => {
        editor.load('Hello [user]Anna(anna)[/user]');
        const paragraph = root.querySelector('p')!;
        select(paragraph, paragraph.childNodes.length);
        const bookmark = editor.captureEntitySelection()!;
        editor.insertText('x');
        editor.undo();
        const recovered = editor.resolveEntitySelection(bookmark)!;
        expect(recovered.collapsed).toBe(true);
        editor.insertMarkdown('[faq]Question(/courses/1/faq?faqId=1)[/faq]', recovered);
        expect(changed).toHaveBeenLastCalledWith('Hello [user]Anna(anna)[/user][faq]Question(/courses/1/faq?faqId=1)[/faq]');
    });

    it('recovers the insertion point after changing the paragraph to a list', () => {
        editor.load('Before');
        select(root.querySelector('p')!.firstChild!, 6);
        const bookmark = editor.captureEntitySelection()!;
        editor.block('ul');
        editor.insertMarkdown('[faq]Question(/courses/1/faq?faqId=1)[/faq]', editor.resolveEntitySelection(bookmark));
        expect(changed).toHaveBeenLastCalledWith('- Before[faq]Question(/courses/1/faq?faqId=1)[/faq]');
    });

    it('retains a pending attachment insertion point when another paragraph becomes a typed list', () => {
        editor.load('Before after\n\nLater');
        select(root.querySelector('p')!.firstChild!, 7);
        const pendingUpload = editor.captureEntitySelection()!;
        const later = root.querySelectorAll('p')[1];
        select(later.firstChild!, 5);
        editor.insertText('\n-');
        expect(editor.convertTypedListMarker(' ')).toBe(true);
        editor.insertText('Item');

        editor.insertMarkdown('![pending.png](/pending.png)', editor.resolveEntitySelection(pendingUpload));

        expect(changed).toHaveBeenLastCalledWith('Before ![pending.png](/pending.png)after\n\nLater\n\n- Item');
    });

    it('rejects insertion into code and unrecognized bookmarks', () => {
        editor.load('`code`');
        select(root.querySelector('code')!.firstChild!, 2);
        expect(editor.captureEntitySelection()).toBeUndefined();
        expect(editor.resolveEntitySelection(document.createRange())).toBeUndefined();
    });

    it('maps an upload caret across text undone before it without splitting the following word', () => {
        editor.load('Before after');
        select(root.querySelector('p')!.firstChild!, 7);
        editor.insertText('🎉');
        const bookmark = editor.captureEntitySelection()!;
        editor.undo();
        editor.insertMarkdown('[faq]Question(/courses/1/faq?faqId=1)[/faq] ', editor.resolveEntitySelection(bookmark));
        expect(changed).toHaveBeenLastCalledWith('Before [faq]Question(/courses/1/faq?faqId=1)[/faq] after');
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

    it('keeps a visible insertion point after a trailing paragraph line break without serializing its placeholder', () => {
        editor.load('First');
        const paragraph = root.querySelector('p')!;
        select(paragraph.firstChild!, 5);
        editor.insertText('\n');
        expect(paragraph.querySelector('br[data-message-placeholder]')).not.toBeNull();
        expect(changed).toHaveBeenLastCalledWith('First');
        editor.insertText('Second');
        expect(changed).toHaveBeenLastCalledWith('First\nSecond');
        expect(paragraph.querySelector('[data-message-placeholder]')).toBeNull();
        editor.undo();
        expect(changed).toHaveBeenLastCalledWith('First');
        editor.redo();
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

    it('keeps native cross-paragraph typing and merges paragraph boundaries when pasting plain text', () => {
        editor.load('First paragraph\n\nSecond paragraph');
        const paragraphs = root.querySelectorAll('p');
        const range = document.createRange();
        range.setStart(paragraphs[0].firstChild!, 6);
        range.setEnd(paragraphs[1].firstChild!, 6);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        expect(editor.needsManagedTextInsertion()).toBe(false);
        editor.insertText('new');
        expect(root.querySelectorAll('p')).toHaveLength(1);
        expect(root.textContent).toBe('First new paragraph');
        expect(changed).toHaveBeenLastCalledWith('First new paragraph');
        editor.undo();
        expect(root.querySelectorAll('p')).toHaveLength(2);
        expect(window.getSelection()!.toString()).toBe('paragraphSecond');
        editor.redo();
        expect(root.textContent).toBe('First new paragraph');
    });

    it('finds an existing link from a keyboard selection and updates it without nesting anchors', () => {
        editor.load('Read [**guide**](https://example.org/old) next');
        const link = root.querySelector('a')!;
        select(link.querySelector('strong')!.firstChild!, 2);
        expect(editor.linkAtSelection()).toBe(link);
        expect(editor.canLinkSelection()).toBe(true);
        editor.setLink('guide', 'https://example.org/new');
        expect(root.querySelectorAll('a')).toHaveLength(1);
        expect(root.querySelector('a a')).toBeNull();
        expect(root.querySelector('a strong')!.textContent).toBe('guide');
        expect(link.getAttribute('href')).toBe('https://example.org/new');
        expect(changed).toHaveBeenLastCalledWith('Read [**guide**](https://example.org/new) next');
    });

    it('keeps mixed inline marks when creating a link and after undo, redo and reload', () => {
        editor.load('Read **bold** and *italic* next');
        const range = document.createRange();
        range.setStart(root.querySelector('strong')!.firstChild!, 0);
        range.setEnd(root.querySelector('em')!.firstChild!, 6);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);

        editor.setLink('bold and italic', 'https://example.org/guide');

        expect(root.querySelector('a strong')?.textContent).toBe('bold');
        expect(root.querySelector('a em')?.textContent).toBe('italic');
        expect(changed).toHaveBeenLastCalledWith('Read [**bold** and *italic*](https://example.org/guide) next');
        editor.undo();
        expect(root.querySelector('a')).toBeNull();
        expect(root.querySelector('strong')?.textContent).toBe('bold');
        editor.redo();
        expect(root.querySelector('a strong')?.textContent).toBe('bold');
        editor.load(changed.mock.lastCall![0]);
        expect(root.querySelector('a em')?.textContent).toBe('italic');
        expect(root.querySelector('a')?.getAttribute('href')).toBe('https://example.org/guide');
    });

    it.each([
        [0, 26, 'Read guide and notes today', '[Read **guide** and *notes* today](https://example.org/new)', 1],
        [7, 26, 'ide and notes today', 'Read [**gu**](https://example.org/old)[**ide** and *notes* today](https://example.org/new)', 2],
        [0, 7, 'Read gu', '[Read **gu**](https://example.org/new)[**ide**](https://example.org/old) and [*notes*](https://example.org/notes) today', 3],
    ] as const)('relinks a selection from %s to %s without nesting copied anchors', (start, end, label, expected, linkCount) => {
        editor.load('Read [**guide**](https://example.org/old) and [*notes*](https://example.org/notes) today');
        const first = root.querySelector('p')!.firstChild!;
        const last = root.querySelector('p')!.lastChild!;
        const range = document.createRange();
        if (start === 7) {
            range.setStart(root.querySelector('strong')!.firstChild!, 2);
        } else {
            range.setStart(first, 0);
        }
        if (end === 7) {
            range.setEnd(root.querySelector('strong')!.firstChild!, 2);
        } else {
            range.setEnd(last, last.textContent!.length);
        }
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        expect(range.toString()).toBe(label);

        editor.setLink(label, 'https://example.org/new');

        expect(root.querySelector('a a')).toBeNull();
        expect(root.querySelectorAll('a')).toHaveLength(linkCount);
        expect(root.querySelector('a[href="https://example.org/new"]')?.textContent).toBe(label);
        expect(changed).toHaveBeenLastCalledWith(expected);
        editor.load(expected);
        expect(root.querySelector('a a')).toBeNull();
        expect(root.querySelectorAll('a')).toHaveLength(linkCount);
        expect(root.querySelector('a[href="https://example.org/new"] strong')).not.toBeNull();
        expect(root.textContent).toBe('Read guide and notes today');
    });

    it.each([
        ['paragraphs', 'First paragraph\n\nSecond paragraph', 'p'],
        ['list items', '- First item\n- Second item', 'li'],
        ['quote paragraphs', '> First paragraph\n>\n> Second paragraph', 'p'],
    ])('rejects links across %s without changing content, selection or undo history', (_kind, message, selector) => {
        editor.load(message);
        const blocks = root.querySelectorAll(selector);
        const range = document.createRange();
        range.setStart(blocks[0].firstChild!, 2);
        range.setEnd(blocks[1].firstChild!, 3);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        const html = root.innerHTML;
        const text = range.toString();

        expect(editor.canLinkSelection()).toBe(false);
        editor.setLink(text, 'https://example.org/guide');

        expect(root.innerHTML).toBe(html);
        expect(window.getSelection()!.toString()).toBe(text);
        expect(root.querySelector('a')).toBeNull();
        expect(changed).not.toHaveBeenCalled();
        editor.undo();
        expect(root.innerHTML).toBe(html);
        expect(changed).not.toHaveBeenCalled();
    });

    for (const reference of ['[user]Anna(anna)[/user]', '[channel]general(7)[/channel]']) {
        it.each(['caret', 'partial', 'sentence'] as const)(`rejects a %s link selection intersecting ${reference}`, (kind) => {
            editor.load(`Hello ${reference} today`);
            const item = root.querySelector('[data-message-reference]')!;
            const range = document.createRange();
            if (kind === 'sentence') {
                range.selectNodeContents(root.querySelector('p')!);
            } else {
                range.setStart(item.firstChild!, 1);
                range.setEnd(item.firstChild!, kind === 'caret' ? 1 : 3);
            }
            window.getSelection()!.removeAllRanges();
            window.getSelection()!.addRange(range);
            const html = root.innerHTML;

            expect(editor.canLinkSelection()).toBe(false);
            editor.setLink(range.toString() || 'New link', 'https://example.org/guide');

            expect(root.innerHTML).toBe(html);
            expect(item.getAttribute('data-message-reference')).toBe(reference);
            expect(root.querySelector('a')).toBeNull();
            expect(changed).not.toHaveBeenCalled();
            editor.undo();
            expect(root.innerHTML).toBe(html);
            expect(changed).not.toHaveBeenCalled();
        });
    }

    it('links text beside a protected reference without embedding or changing its payload', () => {
        editor.load('Hello [user]Anna(anna)[/user] today');
        select(root.querySelector('p')!.firstChild!, 0, 5);
        expect(editor.canLinkSelection()).toBe(true);
        editor.setLink('Hello', 'https://example.org/guide');
        expect(root.querySelector('a')?.textContent).toBe('Hello');
        expect(root.querySelector('a [data-message-reference]')).toBeNull();
        expect(root.querySelector('[data-message-reference]')?.getAttribute('data-message-reference')).toBe('[user]Anna(anna)[/user]');
        expect(changed).toHaveBeenLastCalledWith('[Hello](https://example.org/guide) [user]Anna(anna)[/user] today');
    });

    it('links a single paragraph selected from its container without losing the paragraph or inline marks', () => {
        editor.load('Read **course** guide');
        const range = document.createRange();
        range.selectNodeContents(root);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);

        expect(editor.canLinkSelection()).toBe(true);
        editor.setLink('Read course guide', 'https://example.org/guide');

        expect(root.querySelectorAll('p')).toHaveLength(1);
        expect(root.querySelector('p > a strong')?.textContent).toBe('course');
        expect(changed).toHaveBeenLastCalledWith('[Read **course** guide](https://example.org/guide)');
        editor.undo();
        expect(changed).toHaveBeenLastCalledWith('Read **course** guide');
        editor.redo();
        expect(changed).toHaveBeenLastCalledWith('[Read **course** guide](https://example.org/guide)');
        editor.load(changed.mock.lastCall![0]);
        expect(root.querySelector('p > a strong')?.textContent).toBe('course');
        expect(root.querySelector('a')?.getAttribute('href')).toBe('https://example.org/guide');
    });

    it('rejects block selections whose endpoints are the editor container', () => {
        editor.load('First paragraph\n\nSecond paragraph');
        const range = document.createRange();
        range.selectNodeContents(root);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        const html = root.innerHTML;

        expect(editor.canLinkSelection()).toBe(false);
        editor.setLink(range.toString(), 'https://example.org/guide');

        expect(root.innerHTML).toBe(html);
        expect(changed).not.toHaveBeenCalled();
    });

    it('updates and unlinks an existing code-formatted link label without losing its formatting', () => {
        editor.load('Read [`List`](https://example.org/old) next');
        const link = root.querySelector('a')!;
        select(link.querySelector('code')!.firstChild!, 2);
        expect(editor.linkAtSelection()).toBe(link);
        expect(editor.canLinkSelection()).toBe(true);
        editor.setLink('List', 'https://example.org/new');
        expect(root.querySelectorAll('a')).toHaveLength(1);
        expect(root.querySelector('a code')!.textContent).toBe('List');
        expect(changed).toHaveBeenLastCalledWith('Read [`List`](https://example.org/new) next');
        editor.unlink(link);
        expect(root.querySelector('a')).toBeNull();
        expect(root.querySelector('code')!.textContent).toBe('List');
        expect(changed).toHaveBeenLastCalledWith('Read `List` next');
    });

    it.each(['`literal`', '```\nliteral\n```'])('rejects link creation in code: %s', (message) => {
        editor.load(message);
        const text = root.querySelector('code')!.firstChild!;
        select(text, 0, 7);
        expect(editor.canLinkSelection()).toBe(false);
        editor.setLink('literal', 'https://example.org');
        expect(root.querySelector('a')).toBeNull();
        expect(root.querySelector('code')!.textContent).toBe('literal');
        expect(changed).not.toHaveBeenCalled();
    });

    it('rejects a link selection spanning ordinary text and code', () => {
        editor.load('Before `code` after');
        const range = document.createRange();
        range.selectNodeContents(root);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        expect(editor.canLinkSelection()).toBe(false);
        editor.setLink('combined', 'https://example.org');
        expect(root.querySelector('a')).toBeNull();
        expect(root.textContent).toBe('Before code after');
    });

    it('reports active formats at a caret, across a uniform selection and when a pending format is disabled', () => {
        editor.load('- **bold** plain');
        const bold = root.querySelector('strong')!.firstChild!;
        select(bold, 2);
        expect(editor.activeFormatting()).toEqual(expect.arrayContaining(['strong', 'ul']));
        editor.format('strong');
        expect(editor.activeFormatting()).not.toContain('strong');
        expect(editor.activeFormatting()).toContain('ul');
        const item = root.querySelector('li')!;
        select(item, 0, item.childNodes.length);
        expect(editor.activeFormatting()).not.toContain('strong');
        expect(editor.activeFormatting()).toContain('ul');
    });

    it('reports a code block without implying that inline-code formatting is enabled', () => {
        editor.load('```\nliteral\n```');
        select(root.querySelector('code')!.firstChild!, 3);
        expect(editor.activeFormatting()).toContain('pre');
        expect(editor.activeFormatting()).not.toContain('code');
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
