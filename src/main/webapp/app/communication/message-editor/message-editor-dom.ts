import { captureMessageBookmark, messageBookmarkText, restoreMessageBookmark } from './message-editor-bookmarks';
import { preserveMessageSelection, restoreMessageSelectionBoundaries } from './message-editor-selection';
import { formatMessageBlocks } from './message-editor-blocks';
import { editorToMessage, isMessageLinkSafe, messageToEditorHtml } from './message-editor-conversion';
import { formatMessageSelection, messageSelectionOffset, restoreMessageSelection, setMessageSelectionMarks } from './message-editor-formatting';
import { EMOTICON_TO_EMOJI } from 'app/editor/monaco-editor/model/emoticon-emoji.util';
import { DiffMatchPatch } from 'diff-match-patch-typescript';

const inlineMarks = ['strong', 'em', 'ins', 's', 'code'] as const;

interface SelectionPoint {
    path: number[];
    offset: number;
}

interface EditorSnapshot {
    html: string;
    selectionHtml: string;
    typingMarks?: string[];
}

export interface MessageCompletion {
    trigger: '@' | '#' | ':';
    query: string;
    range: Range;
}

/** Owns editable DOM transactions and a single undo history for native input and composer actions. */
export class MessageEditorDom {
    private history: EditorSnapshot[] = [];
    private historyIndex = -1;
    private savedRange?: Range;
    private revision = 0;
    private typingMarks?: string[];
    private typingCaret?: Range;
    private composition?: { start: number; marks?: string[] };
    private readonly entitySelections = new WeakMap<Range, { revision: number; start: number; end: number; text: string }>();
    private readonly diff = new DiffMatchPatch();

    constructor(
        readonly element: HTMLElement,
        private readonly changed: (message: string) => void,
    ) {}

    load(message: string): void {
        this.revision++;
        this.element.innerHTML = messageToEditorHtml(message);
        this.ensureCaret();
        this.savedRange = undefined;
        this.typingMarks = undefined;
        this.typingCaret = undefined;
        this.composition = undefined;
        this.history = [this.snapshot()];
        this.historyIndex = 0;
    }

    /** Saves the active selection before a toolbar or form takes focus. */
    rememberSelection(): void {
        const range = this.range();
        this.highlightItem(range);
        this.savedRange = range.cloneRange();
    }

    /** Captures a stable insertion range for asynchronous pickers without embedding references inside code or links. */
    captureEntitySelection(): Range | undefined {
        const range = this.range();
        if (this.parentElement(range.commonAncestorContainer)?.closest('code, pre, a, [data-message-reference]')) {
            return undefined;
        }
        const captured = range.cloneRange();
        const bookmark = captureMessageBookmark(this.element, range);
        this.entitySelections.set(captured, {
            revision: this.revision,
            start: bookmark.start,
            end: bookmark.end,
            text: bookmark.text,
        });
        return captured;
    }

    /** Recovers a picker/upload bookmark after undo or formatting rebuilds the editable DOM. */
    resolveEntitySelection(range: Range): Range | undefined {
        const bookmark = this.entitySelections.get(range);
        if (!bookmark) {
            return undefined;
        }
        if (bookmark.revision !== this.revision) {
            const changes = this.diff.diff_main(bookmark.text, messageBookmarkText(this.element));
            range = restoreMessageBookmark(this.element, this.diff.diff_xIndex(changes, bookmark.start), this.diff.diff_xIndex(changes, bookmark.end));
            const collapsed = range.collapsed;
            const startItem = this.parentElement(range.startContainer)?.closest('[data-message-reference]');
            const endItem = this.parentElement(range.endContainer)?.closest('[data-message-reference]');
            if (startItem) {
                if (collapsed && range.startOffset > 0) {
                    range.setStartAfter(startItem);
                } else {
                    range.setStartBefore(startItem);
                }
                if (collapsed) {
                    range.collapse(true);
                }
            }
            if (endItem && !collapsed) {
                range.setEndAfter(endItem);
            }
        }
        return this.parentElement(range.commonAncestorContainer)?.closest('code, pre, a, [data-message-reference]') ? undefined : range;
    }

    /** Restores the editing selection after focusing the host, which can otherwise move the caret. */
    focus(): void {
        const range = this.range().cloneRange();
        this.element.focus();
        this.select(range);
    }

    needsManagedTextInsertion(): boolean {
        const range = this.range();
        if (!range.collapsed) {
            const nodes = this.selectedTextNodes(range);
            const block = (node: Node) => this.parentElement(node)?.closest('p, li, pre') ?? this.element;
            return nodes.length > 0 && nodes.every((node) => block(node) === block(nodes[0]));
        }
        return (
            this.typingMarks !== undefined ||
            !!this.parentElement(range.startContainer)?.closest('pre') ||
            (range.collapsed &&
                (this.adjacent(range.startContainer, range.startOffset, true) instanceof HTMLBRElement ||
                    (!this.parentElement(range.startContainer)?.closest('a') && this.adjacent(range.startContainer, range.startOffset, true) instanceof HTMLAnchorElement)))
        );
    }

    private range(): Range {
        const selection = this.element.ownerDocument.getSelection();
        if (selection?.rangeCount && this.element.contains(selection.getRangeAt(0).commonAncestorContainer)) {
            const range = selection.getRangeAt(0);
            if (this.typingCaret && (range.startContainer !== this.typingCaret.startContainer || range.startOffset !== this.typingCaret.startOffset || !range.collapsed)) {
                this.typingMarks = undefined;
                this.typingCaret = undefined;
            }
            return range;
        }
        if (this.savedRange && this.element.contains(this.savedRange.commonAncestorContainer)) {
            return this.savedRange;
        }
        const range = this.element.ownerDocument.createRange();
        range.selectNodeContents(this.element);
        range.collapse(false);
        return range;
    }

    private select(range: Range): void {
        const selection = this.element.ownerDocument.getSelection();
        selection?.removeAllRanges();
        selection?.addRange(range);
        this.highlightItem(range);
        this.savedRange = range.cloneRange();
        if (this.typingMarks) {
            this.typingCaret = range.cloneRange();
        }
    }

    /** Removes presentation-only selection state when navigation or pointer input moves the caret. */
    clearItemHighlight(): void {
        this.element.querySelector('[data-message-selected]')?.removeAttribute('data-message-selected');
    }

    private selectedItem(range: Range): HTMLElement | undefined {
        if (range.startContainer === range.endContainer && range.endOffset === range.startOffset + 1) {
            const node = range.startContainer.childNodes[range.startOffset];
            if (this.isItem(node)) {
                return node;
            }
        }
        return undefined;
    }

    private highlightItem(range: Range): void {
        this.clearItemHighlight();
        this.selectedItem(range)?.setAttribute('data-message-selected', '');
    }

    /** Records the pre-edit selection so undo returns to the actual insertion point. */
    beforeInput(): void {
        this.clearItemHighlight();
        if (this.historyIndex >= 0) {
            this.history[this.historyIndex] = this.snapshot();
        }
    }

    /** Native IME owns the DOM until composition commits; remember the chosen typing marks without inserting placeholders. */
    beginComposition(): void {
        const range = this.range();
        this.composition = {
            start: messageSelectionOffset(this.element, range.startContainer, range.startOffset),
            marks: this.typingMarks ?? (!range.collapsed ? this.selectionMarks(range) : undefined),
        };
        this.beforeInput();
    }

    endComposition(): void {
        const composition = this.composition;
        this.composition = undefined;
        const caret = this.range();
        const end = messageSelectionOffset(this.element, caret.endContainer, caret.endOffset);
        if (composition?.marks && end > composition.start) {
            const inserted = restoreMessageSelection(this.element, composition.start, end);
            this.revision++;
            const restored = setMessageSelectionMarks(this.element, inserted, composition.marks);
            restored.collapse(false);
            this.typingMarks = composition.marks;
            this.select(restored);
        }
        this.afterInput();
    }

    /** Commits a browser edit without replacing its DOM or interrupting composition. */
    afterInput(): void {
        this.ensureCaret();
        const snapshot = this.snapshot();
        if (snapshot.html !== this.history[this.historyIndex]?.html) {
            this.history.splice(this.historyIndex + 1);
            this.history.push(snapshot);
            if (this.history.length > 100) {
                this.history.shift();
            }
            this.historyIndex = this.history.length - 1;
        }
        this.savedRange = this.range().cloneRange();
        this.changed(editorToMessage(this.element));
    }

    private ensureCaret(): void {
        // Browsers need a second, invisible break to keep typing after a trailing line break.
        // Include inline parents too: Shift+Enter can be inserted inside a formatted run.
        const containers = new Set([...this.element.querySelectorAll('pre > code'), ...Array.from(this.element.querySelectorAll('br'), (lineBreak) => lineBreak.parentElement!)]);
        for (const container of containers) {
            const placeholder = container.querySelector(':scope > br[data-message-placeholder]');
            let last = placeholder?.previousSibling ?? container.lastChild;
            while (last?.nodeType === Node.TEXT_NODE && !last.textContent) {
                last = last.previousSibling;
            }
            if (last instanceof HTMLBRElement || last?.textContent?.endsWith('\n')) {
                if (!placeholder) {
                    const end = this.element.ownerDocument.createElement('br');
                    end.setAttribute('data-message-placeholder', '');
                    container.append(end);
                }
            } else {
                placeholder?.remove();
            }
        }
    }

    private transact(change: () => void): void {
        this.beforeInput();
        change();
        this.afterInput();
    }

    insertText(text: string): void {
        this.transact(() => {
            const fragment = this.element.ownerDocument.createDocumentFragment();
            text.split('\n').forEach((line, index) => {
                if (index) {
                    fragment.append(this.element.ownerDocument.createElement('br'));
                }
                fragment.append(this.element.ownerDocument.createTextNode(line));
            });
            const range = this.range();
            const marks = this.typingMarks ?? (!range.collapsed ? this.selectionMarks(range) : undefined);
            const first = fragment.firstChild;
            const last = fragment.lastChild;
            this.replace(fragment);
            if (marks && first && last) {
                const inserted = this.element.ownerDocument.createRange();
                inserted.setStartBefore(first);
                inserted.setEndAfter(last);
                this.revision++;
                const caret = setMessageSelectionMarks(this.element, inserted, marks);
                caret.collapse(false);
                this.typingMarks = marks;
                this.select(caret);
            }
        });
    }

    insertMarkdown(message: string, range?: Range): void {
        this.typingMarks = undefined;
        this.typingCaret = undefined;
        this.transact(() => {
            if (range) {
                this.select(range);
            }
            const template = this.element.ownerDocument.createElement('template');
            template.innerHTML = messageToEditorHtml(message);
            // Completion inserts inline content, not a paragraph inside the current paragraph.
            const paragraph = template.content.firstElementChild;
            const fragment = this.element.ownerDocument.createDocumentFragment();
            if (paragraph?.tagName === 'P' && template.content.childElementCount === 1) {
                fragment.append(...Array.from(paragraph.childNodes));
            } else {
                fragment.append(template.content);
            }
            // Markdown renderers trim trailing spaces. Keep an editable text position after an atomic item.
            const trailingSpace = /[ \t]+$/.exec(message)?.[0];
            if (trailingSpace) {
                fragment.append(this.element.ownerDocument.createTextNode(trailingSpace));
            }
            this.replace(fragment);
        });
    }

    private replace(node: Node): void {
        const range = this.range();
        const firstParagraph = this.parentElement(range.startContainer)?.closest('p');
        const lastParagraph = this.parentElement(range.endContainer)?.closest('p');
        const mergeParagraphs = firstParagraph && lastParagraph && firstParagraph !== lastParagraph && firstParagraph.parentNode === lastParagraph.parentNode;
        range.deleteContents();
        if (mergeParagraphs) {
            range.setStart(firstParagraph, firstParagraph.childNodes.length);
            range.collapse(true);
        }
        const last = node instanceof DocumentFragment ? node.lastChild : node;
        range.insertNode(node);
        if (mergeParagraphs) {
            firstParagraph.append(...Array.from(lastParagraph.childNodes));
            lastParagraph.remove();
        }
        if (last) {
            if (last.nodeType === Node.TEXT_NODE) {
                if (last.textContent) {
                    range.setStart(last, last.textContent.length);
                } else {
                    // WebKit resolves an empty text-node caret before the preceding line break.
                    range.setStartBefore(last);
                }
            } else {
                range.setStartAfter(last);
            }
        }
        range.collapse(true);
        this.select(range);
    }

    private selectedTextNodes(range: Range): Node[] {
        const start = messageSelectionOffset(this.element, range.startContainer, range.startOffset);
        const end = messageSelectionOffset(this.element, range.endContainer, range.endOffset);
        const walker = this.element.ownerDocument.createTreeWalker(this.element, NodeFilter.SHOW_TEXT);
        const selected: Node[] = [];
        let position = 0;
        let node: Node | null;
        while ((node = walker.nextNode())) {
            const length = node.textContent?.length ?? 0;
            if (position < end && position + length > start) selected.push(node);
            position += length;
        }
        return selected;
    }

    /** Native browsers differ when replacement starts outside a mark; retain only formats covering the entire selected text. */
    private selectionMarks(range: Range): string[] {
        const selected = this.selectedTextNodes(range);
        return selected.length
            ? inlineMarks.filter((mark) => selected.every((node) => this.parentElement(node)?.closest(mark) && !this.parentElement(node)?.closest('pre, [data-message-reference]')))
            : [];
    }

    format(tag: 'strong' | 'em' | 'ins' | 's' | 'code'): void {
        const range = this.range();
        if (this.parentElement(range.startContainer)?.closest('pre')) {
            return;
        }
        if (range.collapsed) {
            const marks = this.typingMarks ?? inlineMarks.filter((mark) => this.parentElement(range.startContainer)?.closest(mark));
            this.typingMarks = marks.includes(tag) ? marks.filter((mark) => mark !== tag) : [...marks, tag];
            this.typingCaret = range.cloneRange();
            return;
        }
        this.typingMarks = undefined;
        this.typingCaret = undefined;
        this.transact(() => {
            this.revision++;
            this.select(formatMessageSelection(this.element, range, tag));
        });
    }

    /** Changes only the selected blocks while preserving the exact caret and unselected list items. */
    block(tag: 'ul' | 'ol' | 'blockquote' | 'pre'): void {
        this.typingMarks = undefined;
        this.typingCaret = undefined;
        this.transact(() => {
            this.revision++;
            this.select(formatMessageBlocks(this.element, this.range(), tag));
        });
    }

    /** A second Enter on a blank list item returns to normal text consistently across browsers. */
    exitEmptyList(): boolean {
        const range = this.range();
        const item = this.parentElement(range.startContainer)?.closest('li');
        const list = item?.parentElement;
        if (
            !range.collapsed ||
            !item ||
            !list?.matches('ul, ol') ||
            item.textContent?.replace(/\u200b/g, '').trim() ||
            item.querySelector('img, [data-message-reference], ul, ol')
        ) {
            return false;
        }
        this.block(list.tagName.toLowerCase() as 'ul' | 'ol');
        return true;
    }

    setLink(label: string, url: string, existing?: HTMLAnchorElement): void {
        if (!isMessageLinkSafe(url) || (existing && !this.element.contains(existing))) {
            return;
        }
        this.transact(() => {
            if (existing && this.element.contains(existing)) {
                if (existing.textContent !== (label || url)) {
                    // Preserve uniform marks when renaming; URL-only changes retain even mixed label formatting.
                    let content: HTMLElement = existing;
                    while (content.childNodes.length === 1 && content.firstElementChild?.matches('strong, em, ins, s, code')) {
                        content = content.firstElementChild as HTMLElement;
                    }
                    content.textContent = label || url;
                }
                existing.setAttribute('href', url);
                const caret = this.element.ownerDocument.createRange();
                caret.setStartAfter(existing);
                caret.collapse(true);
                this.select(caret);
            } else {
                const link = this.element.ownerDocument.createElement('a');
                link.textContent = label || url;
                link.setAttribute('href', url);
                this.replace(link);
            }
        });
    }

    unlink(link: HTMLAnchorElement): void {
        if (this.element.contains(link)) {
            this.transact(() => {
                const last = link.lastChild;
                const caret = this.element.ownerDocument.createRange();
                caret.setStartBefore(link);
                link.replaceWith(...Array.from(link.childNodes));
                if (last) caret.setStartAfter(last);
                caret.collapse(true);
                this.select(caret);
            });
        }
    }

    /** Expands a boundary deletion to a whole item; repeating the deletion removes that item. */
    deleteItem(backward: boolean): boolean {
        const range = this.range();
        if (!range.collapsed) {
            const selected = this.selectedItem(range);
            if (selected) {
                this.transact(() => {
                    // Consume one separator with the item, retaining surrounding words and other whitespace.
                    const following = selected.nextSibling;
                    if (following?.nodeType === Node.TEXT_NODE && /^[ \u00a0]/.test(following.textContent ?? '')) {
                        range.setEnd(following, 1);
                    }
                    range.deleteContents();
                    this.select(range);
                });
                return true;
            }
            return false;
        }
        let item = this.adjacent(range.startContainer, range.startOffset, backward);
        if (backward && !this.isItem(item)) {
            // Completion leaves one space after an item. It should not require a separate deletion press.
            if (range.startContainer.nodeType === Node.TEXT_NODE && range.startOffset === 1 && /^[ \u00a0]/.test(range.startContainer.textContent ?? '')) {
                item = this.adjacent(range.startContainer, 0, true);
            } else if (range.startContainer.nodeType !== Node.TEXT_NODE && item?.nodeType === Node.TEXT_NODE && /^[ \u00a0]$/.test(item.textContent ?? '')) {
                item = this.adjacent(item, 0, true);
            }
        }
        if (!this.isItem(item)) {
            return false;
        }
        range.selectNode(item);
        this.select(range);
        return true;
    }

    private adjacent(container: Node, offset: number, backward: boolean): Node | undefined {
        let node: Node | undefined;
        if (container.nodeType === Node.TEXT_NODE) {
            if (offset !== (backward ? 0 : container.textContent?.length)) {
                return undefined;
            }
            node = container;
        } else {
            const child = container.childNodes[backward ? offset - 1 : offset];
            if (child) {
                const edge = this.edge(child, backward);
                return edge.nodeType === Node.TEXT_NODE && !edge.textContent ? this.adjacent(edge, 0, backward) : edge;
            }
            node = container;
        }
        while (node && node !== this.element) {
            const sibling: Node | null = backward ? node.previousSibling : node.nextSibling;
            if (sibling) {
                const edge = this.edge(sibling, backward);
                if (edge.nodeType !== Node.TEXT_NODE || edge.textContent) {
                    return edge;
                }
                node = sibling;
            } else {
                node = node.parentNode ?? undefined;
            }
        }
        return undefined;
    }

    private edge(node: Node, backward: boolean): Node {
        while (!this.isItem(node) && node.childNodes.length) {
            node = (backward ? node.lastChild : node.firstChild)!;
        }
        return node;
    }

    private isItem(node?: Node): node is HTMLElement {
        return node instanceof HTMLElement && node.matches('[data-message-reference], a');
    }

    completion(): MessageCompletion | undefined {
        const range = this.range();
        let node: Node | undefined = range.startContainer;
        let offset = range.startOffset;
        if (node.nodeType !== Node.TEXT_NODE) {
            node = node.childNodes[offset - 1];
            while (node?.nodeType === Node.TEXT_NODE && !node.textContent) node = node.previousSibling ?? undefined;
            while (node?.lastChild && !(node instanceof HTMLElement && node.matches('a, code, pre, [data-message-reference]'))) node = node.lastChild;
            offset = node?.textContent?.length ?? 0;
        }
        if (!range.collapsed || node?.nodeType !== Node.TEXT_NODE || this.parentElement(node)?.closest('code, pre, a, [data-message-reference]')) {
            return undefined;
        }
        const before = node.textContent!.slice(0, offset);
        const match = /(?:^|[\s(])([@#:])([^\s@#:]*)$/.exec(before);
        if (!match || (match[1] === ':' && (!/^[A-Za-z0-9_+-]+$/.test(match[2]) || EMOTICON_TO_EMOJI[`:${match[2]}`]))) {
            return undefined;
        }
        const replacement = range.cloneRange();
        replacement.setStart(node, offset - match[2].length - 1);
        replacement.setEnd(node, offset);
        return { trigger: match[1] as '@' | '#' | ':', query: match[2], range: replacement };
    }

    private parentElement(node: Node): HTMLElement | undefined {
        return node instanceof HTMLElement ? node : (node.parentElement ?? undefined);
    }

    undo(): void {
        if (this.historyIndex > 0) {
            this.restore(this.history[--this.historyIndex]);
        }
    }

    redo(): void {
        if (this.historyIndex < this.history.length - 1) {
            this.restore(this.history[++this.historyIndex]);
        }
    }

    private point(node: Node, offset: number): SelectionPoint {
        const path: number[] = [];
        while (node !== this.element && node.parentNode) {
            path.unshift(Array.prototype.indexOf.call(node.parentNode.childNodes, node));
            node = node.parentNode;
        }
        return { path, offset };
    }

    private snapshot(): EditorSnapshot {
        const range = this.range();
        const clone = this.element.cloneNode(true) as HTMLElement;
        const resolve = (point: SelectionPoint) => point.path.reduce<Node>((node, index) => node.childNodes[index], clone);
        const first = this.point(range.startContainer, range.startOffset);
        const last = this.point(range.endContainer, range.endOffset);
        const selection = clone.ownerDocument.createRange();
        selection.setStart(resolve(first), first.offset);
        selection.setEnd(resolve(last), last.offset);
        preserveMessageSelection(clone, selection);
        return { html: this.element.innerHTML, selectionHtml: clone.innerHTML, typingMarks: this.typingMarks };
    }

    private restore(snapshot: EditorSnapshot): void {
        this.revision++;
        this.element.innerHTML = snapshot.selectionHtml;
        this.typingMarks = snapshot.typingMarks;
        this.typingCaret = undefined;
        this.select(restoreMessageSelectionBoundaries(this.element));
        this.changed(editorToMessage(this.element));
    }
}
