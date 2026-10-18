import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { editorToMessage, messageToEditorFragment } from './message-editor-conversion';
import { MessageEditorDom } from './message-editor-dom';

describe('Message editor block formatting', () => {
    let root: HTMLDivElement;
    let editor: MessageEditorDom;
    const changed = vi.fn();

    function select(start: Node, startOffset: number, end = start, endOffset = startOffset): void {
        const range = document.createRange();
        range.setStart(start, startOffset);
        range.setEnd(end, endOffset);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
    }

    function expectMessage(message: string): void {
        expect(editorToMessage(root)).toBe(message);
        expect(changed).toHaveBeenLastCalledWith(message);
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

    it('leaves an unselected nested list and its trailing paragraph in their original order', () => {
        editor.load('- Parent\n\n  - Child\n\n  Tail\n\nAfter');
        const original = root.querySelector('ul')!.outerHTML;
        const after = root.lastElementChild!;
        select(after.firstChild!, 2);
        editor.block('blockquote');
        expect(root.querySelector('ul')!.outerHTML).toBe(original);
        expect(root.textContent).toBe('ParentChildTailAfter');
        expect(root.querySelector('blockquote')!.textContent).toBe('After');
    });

    it('changes a nested list type without moving the trailing paragraph or its parent', () => {
        editor.load('- Parent\n\n  - Child\n\n  Tail');
        const child = root.querySelector('ul ul li')!;
        select(child.firstChild!, 2);
        editor.block('ol');
        expect(root.querySelector('ul > li > ol > li')?.textContent).toBe('Child');
        expect(root.textContent).toBe('ParentChildTail');
        const parent = root.querySelector('ul > li')!;
        expect(parent.firstChild!.textContent).toBe('Parent');
        expect(parent.lastElementChild?.textContent).toBe('Tail');
        expect(window.getSelection()!.isCollapsed).toBe(true);
        editor.insertText('!');
        expect(root.querySelector('ol > li')!.textContent).toBe('Ch!ild');
    });

    it('preserves separate unselected paragraphs within a nested list during a child style change', () => {
        editor.load('- Parent\n\n  Another\n\n  - Child\n\n  Tail');
        const child = root.querySelector('ul ul li')!;
        select(child.firstChild!, 2);
        editor.block('ol');
        const item = root.querySelector('ul > li')!;
        expect(Array.from(item.querySelectorAll(':scope > p'), (paragraph) => paragraph.textContent)).toEqual(['Parent', 'Another', 'Tail']);
        expect(item.querySelector('ol > li')!.textContent).toBe('Child');
        const reopened = document.createElement('div');
        reopened.replaceChildren(messageToEditorFragment(editorToMessage(root)));
        expect(Array.from(reopened.querySelectorAll('ul > li > p'), (paragraph) => paragraph.textContent)).toEqual(['Parent', 'Another', 'Tail']);
        expect(reopened.querySelector('ul > li > ol > li')!.textContent).toBe('Child');
    });

    it.each(['ul', 'ol'] as const)('exits an empty %s item into a paragraph while retaining its preceding items and undo', (tag) => {
        editor.load(tag === 'ul' ? '- First\n- Second' : '1. First\n2. Second');
        const empty = document.createElement('li');
        empty.append(document.createElement('br'));
        root.querySelector(tag)!.append(empty);
        select(empty, 0);
        editor.afterInput();
        expect(editor.exitEmptyList()).toBe(true);
        editor.insertText('Outside');
        expect(root.querySelectorAll('li')).toHaveLength(2);
        expect(root.lastElementChild!.tagName).toBe('P');
        expect(root.lastElementChild!.textContent).toBe('Outside');
        editor.undo();
        editor.undo();
        expect(root.querySelectorAll('li')).toHaveLength(3);
    });

    it.each([
        ['ul', 'ol', '- **First**\n- *Second*', '1. **First**\n2. *Second*'],
        ['ol', 'ul', '1. **First**\n2. *Second*', '- **First**\n- *Second*'],
    ] as const)('converts selected %s items to %s without nesting or duplicating their contents', (source, target, input, expected) => {
        editor.load(input);
        const items = root.querySelectorAll('li');
        select(items[0], 0, items[1], items[1].childNodes.length);

        editor.block(target);

        expectMessage(expected);
        expect(root.querySelector(source)).toBeNull();
        expect(root.querySelectorAll(`${target} > li`)).toHaveLength(2);
        expect(root.querySelector('li li')).toBeNull();
        expect(root.querySelector('strong')!.textContent).toBe('First');
        expect(root.querySelector('em')!.textContent).toBe('Second');
    });

    it('changes only the current list item when switching the list type at a caret', () => {
        editor.load('Before\n\n- First\n- Middle\n- Last\n\nAfter');
        select(root.querySelectorAll('li')[1].firstChild!, 3);

        editor.block('ol');

        expectMessage('Before\n\n- First\n\n1. Middle\n\n- Last\n\nAfter');
        expect(Array.from(root.children, (element) => element.tagName)).toEqual(['P', 'UL', 'OL', 'UL', 'P']);
        expect(root.querySelector('li li')).toBeNull();
    });

    it.each(['ul', 'ol'] as const)('toggles only the current %s item to a paragraph and retains its inline payloads', (tag) => {
        const prefix = tag === 'ul' ? '-' : '1.';
        editor.load(`Before\n\n${prefix} First\n${prefix} **Middle** [Guide](https://example.org) [user]Anna(anna)[/user]\n${prefix} Last\n\nAfter`);
        const middle = root.querySelectorAll('li')[1];
        select(middle.querySelector('strong')!.firstChild!, 3);

        editor.block(tag);

        const marker = tag === 'ul' ? '-' : '1.';
        const lastMarker = tag === 'ul' ? '-' : '3.';
        expectMessage(`Before\n\n${marker} First\n\n**Middle** [Guide](https://example.org) [user]Anna(anna)[/user]\n\n${lastMarker} Last\n\nAfter`);
        expect(Array.from(root.children, (element) => element.tagName)).toEqual(['P', tag.toUpperCase(), 'P', tag.toUpperCase(), 'P']);
        expect(root.querySelectorAll('li')).toHaveLength(2);
        expect(root.querySelector('a')!.getAttribute('href')).toBe('https://example.org');
        expect(root.querySelector('[data-message-reference]')!.getAttribute('data-message-reference')).toBe('[user]Anna(anna)[/user]');
    });

    it('converts a quote to a list while preserving inline marks, references, and surrounding paragraphs', () => {
        editor.load('Before\n\n> **Quoted** [user]Anna(anna)[/user]\n\nAfter');
        select(root.querySelector('strong')!.firstChild!, 2);

        editor.block('ul');

        expectMessage('Before\n\n- **Quoted** [user]Anna(anna)[/user]\n\nAfter');
        expect(root.querySelector('blockquote')).toBeNull();
        expect(root.querySelectorAll('ul > li')).toHaveLength(1);
        expect(root.querySelector('[data-message-reference]')!.getAttribute('contenteditable')).toBe('false');
    });

    it('converts a list item to a quote without retaining the old list wrapper', () => {
        editor.load('Before\n\n- *Quoted* [Guide](https://example.org)\n\nAfter');
        select(root.querySelector('em')!.firstChild!, 2);

        editor.block('blockquote');

        expectMessage('Before\n\n> *Quoted* [Guide](https://example.org)\n\nAfter');
        expect(root.querySelector('ul')).toBeNull();
        expect(root.querySelector('blockquote > p em')!.textContent).toBe('Quoted');
    });

    it.each(['ul', 'ol'] as const)('formats every paragraph touched by a selection as a separate %s item', (tag) => {
        editor.load('Before\n\nFirst **bold**\n\nSecond *emphasis*\n\nAfter');
        const paragraphs = root.querySelectorAll('p');
        select(paragraphs[1].firstChild!, 2, paragraphs[2].querySelector('em')!.firstChild!, 3);

        editor.block(tag);

        const expected = tag === 'ul' ? '- First **bold**\n- Second *emphasis*' : '1. First **bold**\n2. Second *emphasis*';
        expectMessage(`Before\n\n${expected}\n\nAfter`);
        expect(root.querySelectorAll(`${tag} > li`)).toHaveLength(2);
        expect(Array.from(root.children, (element) => element.tagName)).toEqual(['P', tag.toUpperCase(), 'P']);
        expect(window.getSelection()!.toString()).toBe('rst boldSecond emp');
    });

    it('does not format the following paragraph when a selection ends at its start', () => {
        editor.load('First\n\nSecond');
        const paragraphs = root.querySelectorAll('p');
        select(paragraphs[0].firstChild!, 0, paragraphs[1].firstChild!, 0);

        editor.block('ul');

        expectMessage('- First\n\nSecond');
        expect(root.querySelectorAll('li')).toHaveLength(1);
        expect(root.lastElementChild!.tagName).toBe('P');
    });

    it('keeps a selected soft line break within a single list item', () => {
        editor.load('Before\n\nfirst\nsecond\n\nAfter');
        const paragraph = root.querySelectorAll('p')[1];
        select(paragraph.firstChild!, 1, paragraph.lastChild!, 3);

        editor.block('ul');

        expectMessage('Before\n\n- first\n  second\n\nAfter');
        expect(root.querySelectorAll('li')).toHaveLength(1);
        expect(root.querySelectorAll('li br:not([data-message-placeholder])')).toHaveLength(1);
        expect(window.getSelection()!.toString()).toBe('irstsec');
    });

    it.each(['ul', 'ol'] as const)('starts a %s on the second line after Shift+Enter and retains its caret and undo history', (tag) => {
        editor.load('First');
        select(root.querySelector('p')!.firstChild!, 5);
        editor.insertText('\n');
        editor.insertText('Second');

        editor.block(tag);

        const marker = tag === 'ul' ? '-' : '1.';
        expectMessage(`First\n\n${marker} Second`);
        expect(root.querySelectorAll('li')).toHaveLength(1);
        editor.undo();
        expectMessage('First\nSecond');
        editor.redo();
        expectMessage(`First\n\n${marker} Second`);
        editor.insertText('!');
        expectMessage(`First\n\n${marker} Second!`);
    });

    it.each([
        [0, '- First\n\nSecond\nThird'],
        [1, 'First\n\n- Second\n\nThird'],
        [2, 'First\nSecond\n\n- Third'],
    ] as const)('applies a list only to soft line %i and keeps the remaining soft lines together', (line, expected) => {
        editor.load('First\nSecond\nThird');
        const text = Array.from(root.querySelector('p')!.childNodes).filter((node) => node.nodeType === Node.TEXT_NODE);
        select(text[line], 2);

        editor.block('ul');

        expectMessage(expected);
        expect(root.querySelectorAll('li')).toHaveLength(1);
    });

    it('retains marks that cross a soft break and protected references on the chosen line', () => {
        editor.load('**First\nSecond** [user]Anna(anna)[/user]\nThird');
        select(root.querySelector('strong')!.lastChild!, 3);

        editor.block('ul');

        expectMessage('**First**\n\n- **Second** [user]Anna(anna)[/user]\n\nThird');
        expect(root.querySelector('li strong')!.textContent).toBe('Second');
        expect(root.querySelector('p strong')!.textContent).toBe('First');
        expect(root.querySelector('[data-message-reference]')!.getAttribute('data-message-reference')).toBe('[user]Anna(anna)[/user]');
        editor.insertText('!');
        expect(root.querySelector('li strong')!.textContent).toBe('Sec!ond');
    });

    it('starts an empty current soft line as a list without absorbing its preceding line', () => {
        editor.load('First');
        select(root.querySelector('p')!.firstChild!, 5);
        editor.insertText('\n');

        editor.block('ul');
        editor.insertText('Second');

        expectMessage('First\n\n- Second');
        expect(root.querySelector('p')!.textContent).toBe('First');
    });

    it('starts a list on the current soft line when native editing has no paragraph wrapper', () => {
        editor.load('');
        editor.insertText('First\nSecond');

        editor.block('ul');

        expectMessage('First\n\n- Second');
    });

    it('keeps an existing list item and its soft continuation together when changing list type', () => {
        editor.load('- First\n  Second');
        select(root.querySelector('li')!.lastChild!, 2);

        editor.block('ol');

        expectMessage('1. First\n   Second');
        expect(root.querySelectorAll('li')).toHaveLength(1);
    });

    it('converts a real code block as one item when its caret is on a later code line', () => {
        editor.load('```\nFirst\nSecond\n```');
        select(root.querySelector('code')!.lastChild!, 2);

        editor.block('ul');

        expectMessage('- First\n  Second');
        expect(root.querySelectorAll('li')).toHaveLength(1);
        expect(root.querySelector('pre')).toBeNull();
    });

    it('toggles only selected list items to paragraphs while leaving the preceding item in its list', () => {
        editor.load('Before\n\n- First\n- Middle\n- Last\n\nAfter');
        const items = root.querySelectorAll('li');
        select(items[1].firstChild!, 2, items[2].firstChild!, 3);

        editor.block('ul');

        expectMessage('Before\n\n- First\n\nMiddle\n\nLast\n\nAfter');
        expect(root.querySelectorAll('ul > li')).toHaveLength(1);
        expect(root.querySelector('li')!.textContent).toBe('First');
        expect(window.getSelection()!.toString()).toBe('ddleLas');
    });

    it('handles paragraph wrappers inside loose list items without introducing nested lists', () => {
        editor.load('- **First**\n\n- *Second*');
        const items = root.querySelectorAll('li');
        select(items[0].querySelector('strong')!.firstChild!, 1, items[1].querySelector('em')!.firstChild!, 3);

        editor.block('ol');

        expectMessage('1. **First**\n2. *Second*');
        expect(root.querySelector('ul, li li')).toBeNull();
        expect(root.querySelectorAll('ol > li')).toHaveLength(2);
    });

    it('quotes all selected paragraphs together without changing adjacent paragraphs', () => {
        editor.load('Before\n\nFirst\n\nSecond\n\nAfter');
        const paragraphs = root.querySelectorAll('p');
        select(paragraphs[1].firstChild!, 2, paragraphs[2].firstChild!, 3);

        editor.block('blockquote');

        expectMessage('Before\n\n> First\n> \n> Second\n\nAfter');
        expect(root.querySelectorAll('blockquote > p')).toHaveLength(2);
        expect(Array.from(root.children, (element) => element.tagName)).toEqual(['P', 'BLOCKQUOTE', 'P']);
    });

    it('converts all selected paragraphs to one code block with their paragraph break intact', () => {
        editor.load('Before\n\nFirst\n\nSecond\n\nAfter');
        const paragraphs = root.querySelectorAll('p');
        select(paragraphs[1].firstChild!, 1, paragraphs[2].firstChild!, 3);

        editor.block('pre');

        expectMessage('Before\n\n```\nFirst\n\nSecond\n```\n\nAfter');
        expect(root.querySelectorAll('pre')).toHaveLength(1);
        expect(Array.from(root.children, (element) => element.tagName)).toEqual(['P', 'PRE', 'P']);
    });

    it('converts a multiline quote to code without retaining the quote wrapper', () => {
        editor.load('Before\n\n> first\n> second\n\nAfter');
        select(root.querySelector('blockquote p')!.firstChild!, 2);

        editor.block('pre');

        expectMessage('Before\n\n```\nfirst\nsecond\n```\n\nAfter');
        expect(root.querySelector('blockquote')).toBeNull();
        expect(root.querySelector('pre code code')).toBeNull();
    });

    it('flattens reference chips, links, and marks to visible code text and restores rich content on undo', () => {
        const source = 'Before\n\n**Hello** [Guide](https://example.org) [user]Anna(anna)[/user]\n[channel]general(42)[/channel] ![diagram](/diagram.png)\n\nAfter';
        editor.load(source);
        const paragraph = root.querySelectorAll('p')[1];
        const original = paragraph.innerHTML;
        select(paragraph, 0, paragraph, paragraph.childNodes.length);

        editor.block('pre');

        const expected = 'Before\n\n```\nHello Guide @Anna\n#general diagram\n```\n\nAfter';
        expect(root.querySelector('pre code [data-message-reference], pre a, pre strong, pre img')).toBeNull();
        expect(root.querySelector('pre code')!.innerHTML).toBe('Hello Guide @Anna<br>#general diagram');
        expectMessage(expected);
        const reopened = document.createElement('div');
        reopened.replaceChildren(messageToEditorFragment(expected));
        expect(reopened.querySelector('pre code')!.innerHTML).toBe('Hello Guide @Anna<br>#general diagram');

        editor.undo();
        expectMessage(source);
        expect(root.querySelectorAll('p')[1].innerHTML).toBe(original);
        expect(root.querySelectorAll('[data-message-reference]')).toHaveLength(3);
        editor.redo();
        expectMessage(expected);
        expect(root.querySelector('pre code [data-message-reference]')).toBeNull();
    });

    it('converts multiline code to a quote without leaving code formatting or losing newlines', () => {
        editor.load('Before\n\n```\nfirst\nsecond\n```\n\nAfter');
        select(root.querySelector('pre code')!.firstChild!, 2);

        editor.block('blockquote');

        expectMessage('Before\n\n> first\n> second\n\nAfter');
        expect(root.querySelector('pre, code')).toBeNull();
        expect(root.querySelector('blockquote > p')).not.toBeNull();
    });

    it('keeps the caret at the same text position when applying and removing a block style', () => {
        editor.load('Before\n\nHello world\n\nAfter');
        select(root.querySelectorAll('p')[1].firstChild!, 5);

        editor.block('blockquote');
        expect(window.getSelection()!.isCollapsed).toBe(true);
        const beforeCaret = document.createRange();
        beforeCaret.selectNodeContents(root);
        const caret = window.getSelection()!.getRangeAt(0);
        beforeCaret.setEnd(caret.startContainer, caret.startOffset);
        expect(beforeCaret.toString()).toBe('BeforeHello');

        editor.block('blockquote');
        expectMessage('Before\n\nHello world\n\nAfter');
        editor.insertText('!');
        expectMessage('Before\n\nHello! world\n\nAfter');
    });

    it('keeps the caret on its original code line when removing multiline code formatting', () => {
        editor.load('Before\n\n```\nfirst\nsecond\n```\n\nAfter');
        select(root.querySelector('pre code')!.lastChild!, 3);

        editor.block('pre');
        editor.insertText('!');

        expectMessage('Before\n\nfirst\nsec!ond\n\nAfter');
        expect(root.querySelector('pre, code')).toBeNull();
    });

    it('restores the selected words and transformed block structure on undo and redo', () => {
        const source = 'Before\n\nFirst paragraph\n\nSecond paragraph\n\nAfter';
        editor.load(source);
        const paragraphs = root.querySelectorAll('p');
        select(paragraphs[1].firstChild!, 6, paragraphs[2].firstChild!, 6);
        const selected = window.getSelection()!.toString();

        editor.block('ul');
        editor.undo();
        expectMessage(source);
        expect(window.getSelection()!.toString()).toBe(selected);

        editor.redo();
        expectMessage('Before\n\n- First paragraph\n- Second paragraph\n\nAfter');
        expect(window.getSelection()!.toString()).toBe(selected);
        expect(root.querySelectorAll('ul > li')).toHaveLength(2);
    });
});
