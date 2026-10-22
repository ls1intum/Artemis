/** Splits the selected item(s) at the caret, preserving the prefix, formatted suffix and nested blocks. */
export function splitMessageListItem(first: HTMLLIElement, last: HTMLLIElement, selection: Range): Range {
    const range = selection.cloneRange();
    range.deleteContents();
    if (first !== last) {
        // Deletion across sibling items collapses at the list boundary. Join the surviving suffix before splitting.
        range.setStart(first, first.childNodes.length);
        range.collapse(true);
        first.append(...Array.from(last.childNodes));
        last.remove();
    }
    const suffix = range.cloneRange();
    suffix.selectNodeContents(first);
    suffix.setStart(range.startContainer, range.startOffset);
    const next = first.ownerDocument.createElement('li');
    next.append(suffix.extractContents());
    first.after(next);
    // Loose lists wrap their text in paragraphs; keep typing inside that paragraph.
    const start = next.firstChild;
    range.selectNodeContents(start instanceof HTMLParagraphElement ? start : next);
    range.collapse(true);
    return range;
}
