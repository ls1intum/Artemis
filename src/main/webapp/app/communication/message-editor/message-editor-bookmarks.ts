const blockTags = new Set(['P', 'DIV', 'LI', 'PRE', 'BLOCKQUOTE', 'UL', 'OL']);
const blockSeparator = '\u2029';

interface BookmarkPoint {
    node: Node;
    offset: number;
}

interface BookmarkIndex {
    text: string;
    points: BookmarkPoint[];
    offsets: Map<Node, number[]>;
}

/**
 * Gives both DOM boundaries and text characters a coordinate in one diffable document.
 * Soft breaks occupy a newline; content-block endings occupy a paragraph separator.
 * Wrapper-only lists and quotes add nothing, so changing a paragraph's style keeps its coordinates.
 */
function indexMessage(root: HTMLElement): BookmarkIndex {
    const parts: string[] = [];
    const points: BookmarkPoint[] = [];
    const offsets = new Map<Node, number[]>();
    let position = 0;
    const isBlock = (node: Node): boolean => node instanceof HTMLElement && blockTags.has(node.tagName);
    const appendBreak = (value: string, before: BookmarkPoint, after: BookmarkPoint): void => {
        points[position] = before;
        parts.push(value);
        points[++position] = after;
    };

    const visit = (node: Node): void => {
        if (node.nodeType === Node.TEXT_NODE) {
            const value = node.textContent ?? '';
            const nodeOffsets: number[] = [];
            for (let index = 0; index <= value.length; index++) {
                nodeOffsets.push(position + index);
                points[position + index] ??= { node, offset: index };
            }
            offsets.set(node, nodeOffsets);
            parts.push(value);
            position += value.length;
            return;
        }
        const children = Array.from(node.childNodes);
        const nodeOffsets = [position];
        offsets.set(node, nodeOffsets);
        if (node instanceof HTMLBRElement) {
            if (!node.hasAttribute('data-message-placeholder')) {
                const parent = node.parentNode!;
                const index = Array.prototype.indexOf.call(parent.childNodes, node);
                appendBreak('\n', { node: parent, offset: index }, { node: parent, offset: index + 1 });
            }
            return;
        }
        const block = node === root || isBlock(node);
        const hasBlockChildren = children.some(isBlock);
        if (block) {
            points[position] = { node, offset: 0 };
        }
        let inlineRun = false;
        children.forEach((child, index) => {
            const childBlock = isBlock(child);
            if (block && childBlock && inlineRun) {
                // Native contenteditable DOM can mix bare text with paragraph elements.
                const boundary = { node, offset: index };
                appendBreak(blockSeparator, boundary, boundary);
                nodeOffsets[index] = position;
            }
            const before = position;
            visit(child);
            inlineRun = childBlock ? false : inlineRun || position > before;
            nodeOffsets.push(position);
        });
        if (block && ((!hasBlockChildren && node !== root) || inlineRun)) {
            const end = { node, offset: children.length };
            const parent = node.parentNode;
            const after = node !== root && parent ? { node: parent, offset: Array.prototype.indexOf.call(parent.childNodes, node) + 1 } : end;
            appendBreak(blockSeparator, end, after);
        }
        if (node instanceof HTMLElement && node.matches('a, code, pre, [data-message-reference]') && node.parentNode) {
            // An equivalent text boundary must not restore a picker inside an atomic item or code.
            const index = Array.prototype.indexOf.call(node.parentNode.childNodes, node);
            points[nodeOffsets[0]] = { node: node.parentNode, offset: index };
            const end = nodeOffsets[nodeOffsets.length - 1];
            if (end !== nodeOffsets[0]) {
                points[end] = { node: node.parentNode, offset: index + 1 };
            }
        }
    };

    visit(root);
    points[0] ??= { node: root, offset: 0 };
    return { text: parts.join(''), points, offsets };
}

/** Text for diffing asynchronous bookmarks, including empty blocks and visible line breaks. */
export function messageBookmarkText(root: HTMLElement): string {
    return indexMessage(root).text;
}

/** Captures structure-aware positions before a picker takes focus or formatting replaces the DOM. */
export function captureMessageBookmark(root: HTMLElement, range: Range): { text: string; start: number; end: number } {
    const index = indexMessage(root);
    const offset = (node: Node, childOffset: number): number => {
        const positions = index.offsets.get(node);
        return positions?.[Math.max(0, Math.min(childOffset, positions.length - 1))] ?? index.text.length;
    };
    return { text: index.text, start: offset(range.startContainer, range.startOffset), end: offset(range.endContainer, range.endOffset) };
}

/** Restores diff-adjusted positions into current DOM boundaries, clamping removed content. */
export function restoreMessageBookmark(root: HTMLElement, start: number, end: number): Range {
    const index = indexMessage(root);
    const clamp = (offset: number): number => Math.max(0, Math.min(offset, index.text.length));
    const first = clamp(start);
    const last = Math.max(first, clamp(end));
    const from = index.points[first];
    const to = index.points[last];
    const beforePlaceholder = (point: BookmarkPoint): BookmarkPoint => {
        let offset = point.offset;
        if (point.node.nodeType !== Node.TEXT_NODE) {
            while (offset > 0) {
                const previous = point.node.childNodes[offset - 1];
                if ((previous instanceof HTMLBRElement && previous.hasAttribute('data-message-placeholder')) || (previous?.nodeType === Node.TEXT_NODE && !previous.textContent)) {
                    offset--;
                } else {
                    break;
                }
            }
        }
        return { node: point.node, offset };
    };
    const startPoint = beforePlaceholder(from);
    const endPoint = beforePlaceholder(to);
    const range = root.ownerDocument.createRange();
    range.setStart(startPoint.node, startPoint.offset);
    range.setEnd(endPoint.node, endPoint.offset);
    return range;
}
