import { MESSAGE_CLIPBOARD_TYPE, clipboardToEditorFragment, decodeMessageClipboard, encodeMessageClipboard } from './message-editor-clipboard';
import { captureMessageBookmark, messageBookmarkText, restoreMessageBookmark } from './message-editor-bookmarks';
import { preserveMessageSelection, restoreMessageSelectionBoundaries } from './message-editor-selection';
import { formatMessageBlocks } from './message-editor-blocks';
import { editorToMessage, isMessageLinkSafe, messageToEditorFragment, protectMessagePostReferences, splitProtectedMessageMarks } from './message-editor-conversion';
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
    selectionContent: HTMLElement;
    typingMarks?: string[];
}

/** A trigger/query pair and the native text range that accepting a suggestion must replace. */
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

    /** Replaces the document from Markdown and starts a fresh history, discarding saved selections and composition state. */
    load(message: string): void {
        this.revision++;
        this.element.replaceChildren(messageToEditorFragment(message));
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

    /** Reports when insertion must use the adapter to preserve typing marks, code, or a caret beside a break or link. */
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

    /** Applies the pending marks to the committed IME text and records the resulting edit in the shared undo history. */
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
    afterInput(native = true): void {
        this.ensureCaret(native);
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

    private ensureCaret(native = false): void {
        // Browsers need a second, invisible break to keep typing after a trailing line break.
        // Include inline parents too: Shift+Enter can be inserted inside a formatted run.
        const containers = new Set([
            ...this.element.querySelectorAll('p, li, pre > code'),
            ...Array.from(this.element.querySelectorAll('br'), (lineBreak) => lineBreak.parentElement!),
        ]);
        for (const container of containers) {
            let placeholder = container.querySelector(':scope > br[data-message-placeholder]');
            let last = placeholder?.previousSibling ?? container.lastChild;
            while (last?.nodeType === Node.TEXT_NODE && !last.textContent) {
                last = last.previousSibling;
            }
            // A browser-created empty item/paragraph already has its layout break. A second break becomes a real,
            // deletable empty line and prevents Backspace from merging the paragraph back into its preceding item.
            if (native && last instanceof HTMLBRElement && !placeholder && !container.textContent && container.childNodes.length === 1) {
                last.setAttribute('data-message-placeholder', '');
                continue;
            }
            if (!container.childNodes.length) {
                const empty = this.element.ownerDocument.createElement('br');
                empty.setAttribute('data-message-placeholder', '');
                container.append(empty);
                const selection = this.element.ownerDocument.getSelection();
                const range = selection?.rangeCount ? selection.getRangeAt(0) : undefined;
                if (range?.collapsed && container.contains(range.startContainer)) {
                    range.setStartBefore(empty);
                    range.collapse(true);
                    this.select(range);
                }
                continue;
            }
            if (last instanceof HTMLBRElement || last?.textContent?.endsWith('\n')) {
                if (!placeholder) {
                    const end = this.element.ownerDocument.createElement('br');
                    end.setAttribute('data-message-placeholder', '');
                    container.append(end);
                    placeholder = end;
                }
                const selection = this.element.ownerDocument.getSelection();
                const range = selection?.rangeCount ? selection.getRangeAt(0) : undefined;
                if (range?.collapsed && container.contains(range.startContainer)) {
                    const suffix = range.cloneRange();
                    suffix.selectNodeContents(container);
                    suffix.setStart(range.startContainer, range.startOffset);
                    const remaining = suffix.cloneContents();
                    remaining.querySelectorAll('[data-message-placeholder]').forEach((item) => item.remove());
                    if (!remaining.textContent && !remaining.querySelector('br, img, [data-message-reference]')) {
                        // Select the visible blank line after its layout placeholder exists. Keeping the old boundary
                        // before an empty text node can leave the browser painting the caret on the preceding line.
                        range.setStartBefore(placeholder);
                        range.collapse(true);
                        this.select(range);
                    }
                }
            } else {
                placeholder?.remove();
            }
        }
    }

    private transact(change: () => void): void {
        this.beforeInput();
        change();
        this.afterInput(false);
    }

    /** Replaces the selection with literal text and soft line breaks, retaining applicable marks as one undoable edit. */
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

    /** Converts a leading marker plus a typed space into a list as one undoable edit; pasted text stays literal. */
    convertTypedListMarker(text: string): boolean {
        if (text !== ' ') return false;
        const range = this.range();
        const parent = this.parentElement(range.startContainer);
        if (!range.collapsed || parent?.closest('pre, code, a, li, blockquote, [data-message-reference]')) return false;
        const paragraph = parent?.closest('p, div') ?? this.element;
        const prefix = range.cloneRange();
        prefix.selectNodeContents(paragraph);
        prefix.setEnd(range.startContainer, range.startOffset);
        const previousBreak = Array.from(paragraph.querySelectorAll('br:not([data-message-placeholder])')).findLast((lineBreak) => prefix.intersectsNode(lineBreak));
        if (previousBreak) prefix.setStartAfter(previousBreak);
        if (prefix.cloneContents().querySelector('code, a, [data-message-reference]')) return false;
        const marker = prefix.toString();
        if (!/^([-*+]|1[.)])$/.test(marker)) return false;
        this.transact(() => {
            this.revision++;
            prefix.deleteContents();
            prefix.collapse(true);
            this.select(prefix);
            this.select(formatMessageBlocks(this.element, prefix, marker.startsWith('1') ? 'ol' : 'ul'));
        });
        return true;
    }

    /** Leaves a code block or quote at its edge, or on Enter after an empty final line, preserving the formatted block. */
    leaveBlock(key: 'ArrowUp' | 'ArrowDown' | 'Enter'): boolean {
        const range = this.range();
        const parent = this.parentElement(range.startContainer);
        const block = parent?.closest('pre, blockquote');
        if (!range.collapsed || !block) return false;
        const prefix = range.cloneRange();
        prefix.selectNodeContents(block);
        prefix.setEnd(range.startContainer, range.startOffset);
        const suffix = range.cloneRange();
        suffix.selectNodeContents(block);
        suffix.setStart(range.endContainer, range.endOffset);
        const before = this.rangeText(prefix);
        const after = this.rangeText(suffix);
        const above = key === 'ArrowUp';
        const line = parent?.closest('p, div');
        const emptyLine = line && line !== block && block.contains(line) && !line.textContent?.trim() && !line.querySelector('img, [data-message-reference]');
        const remaining = above ? before : after;
        if ((remaining && !(emptyLine && !remaining.trim())) || (key === 'Enter' && before && !before.endsWith('\n') && !emptyLine)) return false;
        this.transact(() => {
            if (key === 'Enter') {
                if (emptyLine) {
                    line.remove();
                } else if (before.endsWith('\n')) {
                    if (range.startContainer instanceof Text && range.startContainer.data[range.startOffset - 1] === '\n') {
                        range.startContainer.deleteData(range.startOffset - 1, 1);
                    } else {
                        Array.from(block.querySelectorAll('br:not([data-message-placeholder])'))
                            .findLast((lineBreak) => prefix.intersectsNode(lineBreak))
                            ?.remove();
                    }
                }
            }
            let target = above ? block.previousElementSibling : block.nextElementSibling;
            const reuseParagraph = target?.matches('p, div');
            if (!target || !reuseParagraph) {
                target = this.element.ownerDocument.createElement('p');
                const placeholder = this.element.ownerDocument.createElement('br');
                placeholder.setAttribute('data-message-placeholder', '');
                target.append(placeholder);
                block.insertAdjacentElement(above ? 'beforebegin' : 'afterend', target);
            }
            this.typingMarks = undefined;
            const caret = this.element.ownerDocument.createRange();
            caret.selectNodeContents(target);
            caret.collapse(!above || !reuseParagraph);
            this.select(caret);
        });
        return true;
    }

    private rangeText(range: Range): string {
        const content = range.cloneContents();
        content
            .querySelectorAll('br')
            .forEach((lineBreak) => lineBreak.replaceWith(this.element.ownerDocument.createTextNode(lineBreak.hasAttribute('data-message-placeholder') ? '' : '\n')));
        return (content.textContent ?? '').replace(/\u200b/g, '');
    }

    /** Preserves supported clipboard formatting while code and unsupported clipboard formats retain literal text. */
    insertClipboard(html: string, text: string, internal = ''): void {
        const code = this.parentElement(this.range().startContainer)?.closest('pre, code');
        const fragment = code ? undefined : (decodeMessageClipboard(internal) ?? clipboardToEditorFragment(html));
        if (fragment === undefined) {
            this.insertText(text);
        } else {
            this.insertContent(fragment);
        }
    }

    /** Adds a sanitized wire-format copy for atomic items; native copying remains unchanged for ordinary text. */
    writeClipboard(clipboard: Pick<DataTransfer, 'setData'>, cut = false): boolean {
        const range = this.range().cloneRange();
        if (range.collapsed) return false;
        const startItem = this.parentElement(range.startContainer)?.closest('[data-message-reference]');
        const endItem = this.parentElement(range.endContainer)?.closest('[data-message-reference]');
        if (startItem) range.setStartBefore(startItem);
        if (endItem) range.setEndAfter(endItem);
        const content = this.element.ownerDocument.createElement('div');
        content.append(range.cloneContents());
        if (!content.querySelector('[data-message-reference]')) return false;
        try {
            clipboard.setData(MESSAGE_CLIPBOARD_TYPE, encodeMessageClipboard(content));
            clipboard.setData('text/html', content.innerHTML);
            clipboard.setData('text/plain', startItem || endItem ? this.rangeText(range) : (this.element.ownerDocument.getSelection()?.toString() ?? this.rangeText(range)));
        } catch {
            // If a browser cannot write a custom type, leave its ordinary copy/cut operation intact.
            return false;
        }
        if (cut) {
            this.transact(() => {
                this.revision++;
                this.select(range);
                this.replace(this.element.ownerDocument.createTextNode(''));
            });
        }
        return true;
    }

    /** Inserts sanitized message content at the supplied or remembered range, retaining a trailing completion space. */
    insertMarkdown(message: string, range?: Range): void {
        this.insertContent(messageToEditorFragment(message), range, /[ \t]+$/.exec(message)?.[0]);
    }

    private insertContent(content: DocumentFragment, range?: Range, trailingSpace?: string): void {
        this.typingMarks = undefined;
        this.typingCaret = undefined;
        this.transact(() => {
            if (range) {
                this.select(range);
            }
            // Completion inserts inline content, not a paragraph inside the current paragraph.
            const paragraph = content.firstElementChild;
            const fragment = this.element.ownerDocument.createDocumentFragment();
            if (paragraph?.tagName === 'P' && content.childNodes.length === 1) {
                fragment.append(...Array.from(paragraph.childNodes));
            } else {
                fragment.append(content);
            }
            // Markdown renderers trim trailing spaces. Keep an editable text position after an atomic item.
            if (trailingSpace) {
                fragment.append(this.element.ownerDocument.createTextNode(trailingSpace));
            }
            this.replaceRichContent(fragment);
            this.revision++;
            if (
                Array.from(this.element.querySelectorAll('[data-message-reference]')).some((reference) =>
                    reference.parentElement?.matches('strong, b, em, i, ins, u, s, del, code'),
                )
            ) {
                const restore = preserveMessageSelection(this.element, this.range());
                splitProtectedMessageMarks(this.element);
                this.select(restore());
            }
        });
    }

    /** Splits a surrounding paragraph for block paste, merging its text into the first/last pasted paragraphs. */
    private replaceRichContent(fragment: DocumentFragment): void {
        const blockContent = Array.from(fragment.children).some((child) => child.matches('p, ul, ol, blockquote, pre'));
        const originalParagraph = this.parentElement(this.range().startContainer)?.closest('p');
        if (!blockContent || !originalParagraph) {
            // Pasted content owns its marks/links. Split the surrounding inline wrappers instead of nesting links or
            // making the pasted text inherit unrelated formatting. Both sides keep their original wrappers.
            const caret = this.element.ownerDocument.createTextNode('');
            this.replace(caret);
            let parent = caret.parentElement;
            while (parent?.matches('strong, b, em, i, ins, u, s, del, a')) {
                const following = this.element.ownerDocument.createRange();
                following.selectNodeContents(parent);
                following.setStartAfter(caret);
                const after = parent.cloneNode(false) as HTMLElement;
                after.append(following.extractContents());
                parent.after(caret, after);
                if (!parent.textContent && !parent.querySelector('[data-message-reference], img')) parent.remove();
                if (!after.textContent && !after.querySelector('[data-message-reference], img')) after.remove();
                parent = caret.parentElement;
            }
            const range = this.range();
            range.setStartBefore(caret);
            range.collapse(true);
            this.select(range);
            caret.remove();
            this.replace(fragment);
            return;
        }
        // Reuse selection deletion/paragraph merging before splitting the surviving paragraph at its caret.
        const deletionPoint = this.element.ownerDocument.createTextNode('');
        this.replace(deletionPoint);
        const range = this.range();
        let paragraph = this.parentElement(range.startContainer)?.closest('p');
        if (!paragraph && this.element.contains(originalParagraph)) {
            // Cross-block deletion can collapse at the host boundary; keep the surviving paragraph's prefix.
            paragraph = originalParagraph;
            range.setStart(paragraph, paragraph.childNodes.length);
            range.collapse(true);
            this.select(range);
        }
        deletionPoint.remove();
        if (!paragraph) {
            this.replace(fragment);
            return;
        }
        const suffixRange = range.cloneRange();
        suffixRange.selectNodeContents(paragraph);
        suffixRange.setStart(range.startContainer, range.startOffset);
        const suffix = suffixRange.extractContents();
        const first = fragment.firstElementChild;
        if (first?.matches('p')) {
            paragraph.append(...Array.from(first.childNodes));
            first.remove();
        }
        const last = fragment.lastElementChild;
        const after = last?.matches('p') ? last : this.element.ownerDocument.createElement('p');
        const caret = this.element.ownerDocument.createTextNode('');
        after.append(caret, suffix);
        if (after !== last) fragment.append(after);
        paragraph.after(fragment);
        if (!paragraph.textContent && !paragraph.querySelector('[data-message-reference], img')) paragraph.remove();
        range.setStartBefore(caret);
        range.collapse(true);
        this.select(range);
        caret.remove();
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

    /** Toggles a mark on selected text or the next typed run; fenced code keeps its literal contents unchanged. */
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
            const bookmark = captureMessageBookmark(this.element, range);
            this.element.normalize();
            protectMessagePostReferences(this.element);
            splitProtectedMessageMarks(this.element);
            const selection = restoreMessageBookmark(this.element, bookmark.start, bookmark.end);
            this.select(formatMessageSelection(this.element, selection, tag));
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

    /** Reads the link under a keyboard caret or a selection wholly inside one link. */
    linkAtSelection(): HTMLAnchorElement | undefined {
        const range = this.range();
        const item = this.selectedItem(range);
        const link = item instanceof HTMLAnchorElement ? item : this.parentElement(range.commonAncestorContainer)?.closest('a');
        return link instanceof HTMLAnchorElement && this.element.contains(link) ? link : undefined;
    }

    /** Firefox Select All can select the paragraph node; descend into a sole paragraph to keep its wrapper outside the link. */
    private linkRange(): Range {
        const range = this.range().cloneRange();
        while (range.startContainer === range.endContainer && range.endOffset - range.startOffset === 1) {
            const block = range.startContainer.childNodes[range.startOffset];
            if (!(block instanceof HTMLElement) || !block.matches('p, div')) break;
            range.selectNodeContents(block);
        }
        return range;
    }

    /** Links need inline selections that round-trip through Markdown; existing code-formatted labels remain editable. */
    canLinkSelection(existing?: HTMLAnchorElement): boolean {
        const link = existing ?? this.linkAtSelection();
        if (link) return this.element.contains(link) && !link.closest('code, pre');
        const range = this.linkRange();
        const bookmark = captureMessageBookmark(this.element, range);
        if (
            Array.from(bookmark.text.matchAll(/#\d+/g)).some((match) =>
                range.collapsed
                    ? match.index < bookmark.start && bookmark.start < match.index + match[0].length
                    : match.index < bookmark.end && match.index + match[0].length > bookmark.start,
            )
        ) {
            return false;
        }
        const protectedContent = 'code, pre, [data-message-reference]';
        if (this.parentElement(range.startContainer)?.closest(protectedContent) || this.parentElement(range.endContainer)?.closest(protectedContent)) return false;
        if (range.collapsed) return true;
        // Block wrappers or line breaks inside an anchor would lose their structure when serialized as an inline link.
        if (range.cloneContents().querySelector('p, div, ul, ol, li, blockquote, pre, br, [data-message-reference]')) return false;
        return !Array.from(this.element.querySelectorAll('code, pre')).some((code) => {
            const content = this.element.ownerDocument.createRange();
            content.selectNode(code);
            return content.compareBoundaryPoints(Range.END_TO_START, range) < 0 && content.compareBoundaryPoints(Range.START_TO_END, range) > 0;
        });
    }

    /** Active marks reflect the selection and pending caret formats without inserting empty DOM wrappers. */
    activeFormatting(): string[] {
        const range = this.range();
        const parent = this.parentElement(range.startContainer);
        const marks = parent?.closest('pre') ? [] : range.collapsed ? (this.typingMarks ?? inlineMarks.filter((tag) => parent?.closest(tag))) : this.selectionMarks(range);
        const nodes = range.collapsed ? [range.startContainer] : this.selectedTextNodes(range);
        const blocks = ['ul', 'ol', 'blockquote', 'pre'].filter(
            (tag) =>
                nodes.length &&
                nodes.every((node) => {
                    const element = this.parentElement(node);
                    return tag === 'ul' || tag === 'ol' ? element?.closest('ul, ol')?.tagName.toLowerCase() === tag : !!element?.closest(tag);
                }),
        );
        return [...marks, ...blocks];
    }

    /** Updates an enclosing link or links the selection, rejecting unsafe URLs and new links inside literal code. */
    setLink(label: string, url: string, existing?: HTMLAnchorElement): void {
        existing ??= this.linkAtSelection();
        if (!isMessageLinkSafe(url, label) || !this.canLinkSelection(existing)) {
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
                const selection = this.linkRange();
                if (!selection.collapsed && selection.toString() === label) {
                    // A URL-only operation must preserve each selected inline mark rather than flatten the label.
                    const contents = selection.cloneContents();
                    // Retarget the selected text without nesting an earlier destination inside the new link.
                    contents.querySelectorAll('a').forEach((anchor) => anchor.replaceWith(...Array.from(anchor.childNodes)));
                    link.append(contents);
                } else {
                    link.textContent = label || url;
                }
                link.setAttribute('href', url);
                this.select(selection);
                this.replace(link);
            }
        });
    }

    /** Removes a link belonging to this editor, preserves its formatted label, and leaves the caret after it. */
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

    /** Finds a trigger immediately before a collapsed caret; code, links, protected references, and selections cannot complete. */
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
        // Managed formatted typing can leave one text node per character. Read the same inline run without normalizing
        // the live editing DOM or crossing marks, breaks, links, references or block boundaries.
        const prefix: Node[] = [];
        for (let previous: Node | null = node; previous?.nodeType === Node.TEXT_NODE; previous = previous.previousSibling) {
            prefix.push(previous);
        }
        prefix.reverse();
        const before = prefix.map((part) => (part === node ? part.textContent!.slice(0, offset) : part.textContent!)).join('');
        const match = /(?:^|[\s(])([@#:])([^\s@#:]*)$/.exec(before);
        if (!match || (match[1] === ':' && (!/^[A-Za-z0-9_+-]+$/.test(match[2]) || EMOTICON_TO_EMOJI[`:${match[2]}`]))) {
            return undefined;
        }
        let startOffset = before.length - match[2].length - 1;
        let startNode = node;
        for (const candidate of prefix) {
            if (candidate === node || startOffset < candidate.textContent!.length) {
                startNode = candidate;
                break;
            }
            startOffset -= candidate.textContent!.length;
        }
        const replacement = range.cloneRange();
        replacement.setStart(startNode, startOffset);
        replacement.setEnd(node, offset);
        return { trigger: match[1] as '@' | '#' | ':', query: match[2], range: replacement };
    }

    private parentElement(node: Node): HTMLElement | undefined {
        return node instanceof HTMLElement ? node : (node.parentElement ?? undefined);
    }

    /** Restores the preceding document, typing marks, and native selection, then emits its serialized content. */
    undo(): void {
        if (this.historyIndex > 0) {
            this.restore(this.history[--this.historyIndex]);
        }
    }

    /** Reapplies an undone snapshot, including its typing marks and selection, and emits the restored content. */
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
        return { html: this.element.innerHTML, selectionContent: clone, typingMarks: this.typingMarks };
    }

    private restore(snapshot: EditorSnapshot): void {
        this.revision++;
        const content = snapshot.selectionContent.cloneNode(true);
        this.element.replaceChildren(...Array.from(content.childNodes));
        this.typingMarks = snapshot.typingMarks;
        this.typingCaret = undefined;
        this.select(restoreMessageSelectionBoundaries(this.element));
        this.changed(editorToMessage(this.element));
    }
}
