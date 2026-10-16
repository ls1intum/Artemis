import { editorCodeText, editorToMessage, isMessageLinkSafe, messageToEditorHtml } from './message-editor-conversion';
import { formatMessageSelection } from './message-editor-formatting';

interface SelectionPoint {
    path: number[];
    offset: number;
}

interface EditorSnapshot {
    html: string;
    start?: SelectionPoint;
    end?: SelectionPoint;
}

export interface MessageCompletion {
    trigger: '@' | '#';
    query: string;
    range: Range;
}

/** Owns editable DOM transactions and a single undo history for native input and composer actions. */
export class MessageEditorDom {
    private history: EditorSnapshot[] = [];
    private historyIndex = -1;
    private savedRange?: Range;

    constructor(
        readonly element: HTMLElement,
        private readonly changed: (message: string) => void,
    ) {}

    load(message: string): void {
        this.element.innerHTML = messageToEditorHtml(message);
        this.ensureCaret();
        this.savedRange = undefined;
        this.history = [this.snapshot()];
        this.historyIndex = 0;
    }

    /** Saves the active selection before a toolbar or form takes focus. */
    rememberSelection(): void {
        const range = this.range();
        this.highlightItem(range);
        this.savedRange = range.cloneRange();
    }

    /** Restores the editing selection after focusing the host, which can otherwise move the caret. */
    focus(): void {
        const range = this.range().cloneRange();
        this.element.focus();
        this.select(range);
    }

    needsManagedTextInsertion(): boolean {
        const range = this.range();
        return (
            !!this.parentElement(range.startContainer)?.closest('pre') || (range.collapsed && this.adjacent(range.startContainer, range.startOffset, true) instanceof HTMLBRElement)
        );
    }

    private range(): Range {
        const selection = this.element.ownerDocument.getSelection();
        if (selection?.rangeCount && this.element.contains(selection.getRangeAt(0).commonAncestorContainer)) {
            return selection.getRangeAt(0);
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
            this.replace(fragment);
        });
    }

    insertMarkdown(message: string, range?: Range): void {
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
        range.deleteContents();
        const last = node instanceof DocumentFragment ? node.lastChild : node;
        range.insertNode(node);
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

    format(tag: 'strong' | 'em' | 'ins' | 's' | 'code'): void {
        this.transact(() => {
            const range = this.range();
            if (!range.collapsed) {
                this.select(formatMessageSelection(this.element, range, tag));
                return;
            }
            const ancestor = this.parentElement(range.commonAncestorContainer)?.closest(tag);
            if (ancestor && this.element.contains(ancestor)) {
                // Selection-based removal is supported; do not change unselected text at a collapsed caret.
                return;
            }
            const wrapper = this.element.ownerDocument.createElement(tag);
            wrapper.append(range.extractContents());
            range.insertNode(wrapper);
            range.selectNodeContents(wrapper);
            this.select(range);
        });
    }

    /** Applies a block style to the current paragraph without changing reference payloads. */
    block(tag: 'ul' | 'ol' | 'blockquote' | 'pre'): void {
        this.transact(() => {
            const range = this.range();
            const parent = this.parentElement(range.startContainer);
            const existing = parent?.closest(tag);
            if (existing && this.element.contains(existing)) {
                const replacement = this.element.ownerDocument.createDocumentFragment();
                if (tag === 'ul' || tag === 'ol') {
                    for (const item of Array.from(existing.children)) {
                        const paragraph = this.element.ownerDocument.createElement('p');
                        paragraph.append(...Array.from(item.childNodes));
                        replacement.append(paragraph);
                    }
                } else if (tag === 'pre') {
                    const paragraph = this.element.ownerDocument.createElement('p');
                    editorCodeText(existing)
                        .split('\n')
                        .forEach((line, index) => {
                            if (index) {
                                paragraph.append(this.element.ownerDocument.createElement('br'));
                            }
                            paragraph.append(this.element.ownerDocument.createTextNode(line));
                        });
                    replacement.append(paragraph);
                } else {
                    replacement.append(...Array.from(existing.childNodes));
                }
                const last = replacement.lastChild;
                existing.replaceWith(replacement);
                if (last) {
                    range.selectNodeContents(last);
                    range.collapse(false);
                    this.select(range);
                }
                return;
            }
            const paragraph = parent?.closest('p, div, li, pre');
            const block = this.element.ownerDocument.createElement(tag);
            const child = this.element.ownerDocument.createElement(tag === 'pre' ? 'code' : tag === 'blockquote' ? 'p' : 'li');
            if (paragraph && paragraph !== this.element) {
                child.append(...Array.from(paragraph.childNodes));
                block.append(child);
                if (paragraph.tagName === 'LI') {
                    paragraph.append(block);
                } else {
                    paragraph.replaceWith(block);
                }
            } else {
                block.append(child);
                // A freshly typed contenteditable line can consist of bare inline nodes without a paragraph.
                let first: Node = range.startContainer;
                while (first.parentNode && first.parentNode !== this.element) {
                    first = first.parentNode;
                }
                const isBlock = (node: Node) => node instanceof HTMLElement && node.matches('p, div, ul, ol, blockquote, pre');
                if (first !== this.element && !isBlock(first)) {
                    while (first.previousSibling && !isBlock(first.previousSibling)) {
                        first = first.previousSibling;
                    }
                    this.element.insertBefore(block, first);
                    let next: Node | null = first;
                    while (next && !isBlock(next)) {
                        const following: Node | null = next.nextSibling;
                        child.append(next);
                        next = following;
                    }
                } else {
                    child.append(range.extractContents());
                    this.replace(block);
                }
            }
            range.selectNodeContents(child);
            range.collapse(false);
            this.select(range);
        });
    }

    setLink(label: string, url: string, existing?: HTMLAnchorElement): void {
        if (!isMessageLinkSafe(url)) {
            return;
        }
        this.transact(() => {
            if (existing && this.element.contains(existing)) {
                existing.textContent = label || url;
                existing.setAttribute('href', url);
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
            this.transact(() => link.replaceWith(...Array.from(link.childNodes)));
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
        const node = range.startContainer;
        if (!range.collapsed || node.nodeType !== Node.TEXT_NODE || this.parentElement(node)?.closest('code, pre, a, [data-message-reference]')) {
            return undefined;
        }
        const before = node.textContent!.slice(0, range.startOffset);
        const match = /(?:^|[\s(])([@#])([^\s@#]*)$/.exec(before);
        if (!match) {
            return undefined;
        }
        const replacement = range.cloneRange();
        replacement.setStart(node, range.startOffset - match[2].length - 1);
        return { trigger: match[1] as '@' | '#', query: match[2], range: replacement };
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
        return { html: this.element.innerHTML, start: this.point(range.startContainer, range.startOffset), end: this.point(range.endContainer, range.endOffset) };
    }

    private restore(snapshot: EditorSnapshot): void {
        this.element.innerHTML = snapshot.html;
        const resolve = (point: SelectionPoint) => point.path.reduce<Node | undefined>((node, index) => node?.childNodes[index], this.element);
        if (snapshot.start && snapshot.end) {
            const start = resolve(snapshot.start);
            const end = resolve(snapshot.end);
            if (start && end) {
                const range = this.element.ownerDocument.createRange();
                range.setStart(start, snapshot.start.offset);
                range.setEnd(end, snapshot.end.offset);
                this.select(range);
            }
        }
        this.changed(editorToMessage(this.element));
    }
}
