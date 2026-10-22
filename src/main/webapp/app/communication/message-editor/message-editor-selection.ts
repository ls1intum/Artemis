/**
 * Inserts temporary, text-free boundaries so a DOM transformation can preserve the exact selection, including empty lines.
 * After transforming the marked DOM, call the returned function to remove the boundaries and obtain the restored range.
 */
export function preserveMessageSelection(root: HTMLElement, range: Range): () => Range {
    const marker = () => {
        const element = root.ownerDocument.createElement('span');
        element.setAttribute('data-message-selection-boundary', String(nextBoundary++));
        return element;
    };
    const start = marker();
    const end = range.collapsed ? undefined : marker();
    if (end) {
        const boundary = range.cloneRange();
        boundary.collapse(false);
        boundary.insertNode(end);
    }
    const boundary = range.cloneRange();
    boundary.collapse(true);
    boundary.insertNode(start);
    return () => restoreMessageSelectionBoundaries(root);
}

let nextBoundary = 0;

/**
 * Resolves temporary boundaries to a range, falling back to the document end when none remain.
 * Set remove to false when the marked nodes still need to move; the default removes the boundaries after a transformation.
 */
export function restoreMessageSelectionBoundaries(root: HTMLElement, remove = true): Range {
    const markers = Array.from(root.querySelectorAll('[data-message-selection-boundary]'));
    const first = markers[0];
    const last = markers.length > 1 ? markers.at(-1) : undefined;
    const range = root.ownerDocument.createRange();
    if (first) {
        range.setStartAfter(first);
        if (last) range.setEndBefore(last);
        else range.collapse(true);
    } else {
        range.selectNodeContents(root);
        range.collapse(false);
    }
    if (remove) markers.forEach((marker) => marker.remove());
    return range;
}
