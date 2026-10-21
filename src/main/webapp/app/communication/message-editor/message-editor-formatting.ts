import { preserveMessageSelection } from './message-editor-selection';

const inlineTags = new Set(['STRONG', 'B', 'EM', 'I', 'INS', 'U', 'S', 'DEL', 'CODE', 'A']);
const aliases: Record<string, string> = { B: 'STRONG', I: 'EM', U: 'INS', DEL: 'S' };

// Literal markers carry no formatting; nest them inside code so adjacent code runs still merge.
function markNestingPriority(mark: HTMLElement): number {
    return mark.hasAttribute('data-message-literal') ? 2 : Number(mark.tagName === 'CODE');
}

function canonical(tag: string): string {
    return aliases[tag] ?? tag;
}

/** Merges equivalent neighboring marks so serialization does not create adjacent Markdown delimiters. */
function append(parent: Node, node: Node): void {
    let previous = parent.lastChild;
    const boundaries: Node[] = [];
    while (previous instanceof HTMLElement && previous.hasAttribute('data-message-selection-boundary')) {
        boundaries.unshift(previous);
        previous = previous.previousSibling;
    }
    if (previous instanceof HTMLElement && node instanceof HTMLElement && inlineTags.has(node.tagName) && previous.cloneNode(false).isEqualNode(node.cloneNode(false))) {
        previous.append(...boundaries);
        for (const child of Array.from(node.childNodes)) {
            append(previous, child);
        }
    } else {
        parent.appendChild(node);
    }
}

/** Locates a selection boundary in the document's visible text for DOM-replacement bookmarks. */
export function messageSelectionOffset(root: HTMLElement, container: Node, offset: number): number {
    const prefix = root.ownerDocument.createRange();
    prefix.selectNodeContents(root);
    prefix.setEnd(container, offset);
    return prefix.toString().length;
}

/** Restores selection boundaries in the rebuilt inline runs, including boundaries between marked and plain text. */
export function restoreMessageSelection(root: HTMLElement, start: number, end: number, preferNext = true): Range {
    const point = (offset: number, preferNext: boolean): { node: Node; offset: number } => {
        const walker = root.ownerDocument.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        let node = walker.nextNode();
        let position = 0;
        while (node) {
            const length = node.textContent?.length ?? 0;
            const next = walker.nextNode();
            if (offset < position + length || (offset === position + length && (!preferNext || !next))) {
                return { node, offset: offset - position };
            }
            position += length;
            node = next;
        }
        return { node: root, offset: root.childNodes.length };
    };
    const first = point(start, preferNext);
    const last = start === end ? first : point(end, false);
    const range = root.ownerDocument.createRange();
    range.setStart(first.node, first.offset);
    range.setEnd(last.node, last.offset);
    return range;
}

/**
 * Changes marks on selected inline runs while preserving block structure and unselected text.
 * Protected references and code blocks remain intact. DOM rebuilding happens only for an explicit formatting action.
 */
export function formatMessageSelection(root: HTMLElement, range: Range, tag: string): Range {
    return changeMessageMarks(root, range, tag);
}

/** Applies the chosen typing marks to just the newly inserted text, retaining enclosing link targets. */
export function setMessageSelectionMarks(root: HTMLElement, range: Range, marks: string[]): Range {
    return changeMessageMarks(root, range, undefined, marks);
}

function changeMessageMarks(root: HTMLElement, range: Range, tag?: string, desired?: string[]): Range {
    const start = messageSelectionOffset(root, range.startContainer, range.startOffset);
    const end = messageSelectionOffset(root, range.endContainer, range.endOffset);
    const target = tag?.toUpperCase();
    const restore = preserveMessageSelection(root, range);
    const walker = root.ownerDocument.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let text = walker.nextNode();
    let remove = true;
    let scanPosition = 0;
    while (text) {
        const parent = text.parentElement;
        const length = text.textContent?.length ?? 0;
        if (scanPosition < end && scanPosition + length > start && !parent?.closest('pre, [data-message-reference]')) {
            let ancestor = parent;
            let marked = false;
            while (ancestor && ancestor !== root) {
                marked ||= canonical(ancestor.tagName) === target;
                ancestor = ancestor.parentElement;
            }
            remove &&= marked;
        }
        scanPosition += length;
        text = walker.nextNode();
    }

    const fragment = root.ownerDocument.createDocumentFragment();
    let position = 0;
    const wrap = (parent: Node, node: Node, marks: HTMLElement[]) => {
        for (const mark of marks.toReversed()) {
            const wrapper = mark.cloneNode(false);
            wrapper.appendChild(node);
            node = wrapper;
        }
        append(parent, node);
    };
    const render = (node: Node, parent: Node, marks: HTMLElement[]): void => {
        if (node.nodeType === Node.TEXT_NODE) {
            const value = node.textContent ?? '';
            const from = Math.max(0, Math.min(value.length, start - position));
            const to = Math.max(from, Math.min(value.length, end - position));
            const selectedMarks = desired
                ? [...marks.filter((mark) => mark.tagName === 'A' || mark.hasAttribute('data-message-literal')), ...desired.map((name) => root.ownerDocument.createElement(name))]
                : marks.filter((mark) => canonical(mark.tagName) !== target);
            if (!desired && !remove && tag) {
                selectedMarks.unshift(root.ownerDocument.createElement(tag));
            }
            // Markdown cannot represent emphasis inside inline code; keep code innermost for a faithful round trip.
            selectedMarks.sort((first, second) => markNestingPriority(first) - markNestingPriority(second));
            for (const [part, activeMarks] of [
                [value.slice(0, from), marks],
                [value.slice(from, to), selectedMarks],
                [value.slice(to), marks],
            ] as const) {
                if (part) {
                    wrap(parent, root.ownerDocument.createTextNode(part), activeMarks);
                }
            }
            position += value.length;
        } else if (node instanceof HTMLElement) {
            if (node.matches('[data-message-reference]')) {
                append(parent, node.cloneNode(true));
                position += node.textContent?.length ?? 0;
            } else if (node.matches('pre')) {
                wrap(parent, node.cloneNode(true), marks);
                position += node.textContent?.length ?? 0;
            } else if (inlineTags.has(node.tagName) || node.hasAttribute('data-message-literal')) {
                for (const child of Array.from(node.childNodes)) {
                    render(child, parent, [...marks, node]);
                }
            } else {
                const clone = node.cloneNode(false);
                for (const child of Array.from(node.childNodes)) {
                    render(child, clone, marks);
                }
                append(parent, clone);
            }
        }
    };
    for (const node of Array.from(root.childNodes)) {
        render(node, fragment, []);
    }
    root.replaceChildren(fragment);
    return restore();
}
