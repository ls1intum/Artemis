/** Inserts temporary, text-free boundaries so a DOM transformation can restore the exact selection, including empty lines. */
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

/** Reads the temporary boundaries before moving DOM nodes, or restores and removes them after a transformation. */
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
