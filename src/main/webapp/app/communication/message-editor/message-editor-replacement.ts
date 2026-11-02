/** Deletes a selection, joins sibling paragraphs and inserts content, returning the resulting caret. */
export function replaceMessageRange(selection: Range, node: Node): Range {
    const range = selection.cloneRange();
    const first = paragraphAt(range.startContainer);
    const last = paragraphAt(range.endContainer);
    const merge = first && last && first !== last && first.parentNode === last.parentNode;
    range.deleteContents();
    if (merge) {
        range.setStart(first, first.childNodes.length);
        range.collapse(true);
    }
    const insertedEnd = node instanceof DocumentFragment ? node.lastChild : node;
    range.insertNode(node);
    if (merge) {
        first.append(...Array.from(last.childNodes));
        last.remove();
    }
    if (insertedEnd) {
        if (insertedEnd.nodeType !== Node.TEXT_NODE) range.setStartAfter(insertedEnd);
        else if (insertedEnd.textContent) range.setStart(insertedEnd, insertedEnd.textContent.length);
        // WebKit paints an empty text-node caret before the preceding line break.
        else range.setStartBefore(insertedEnd);
    }
    range.collapse(true);
    return range;
}

/** Pasted content owns its formatting. Preserve surrounding marks without inheriting them or nesting links. */
export function replaceRichMessageRange(root: HTMLElement, selection: Range, fragment: DocumentFragment): Range {
    const paragraph = paragraphAt(selection.startContainer);
    const blocks = Array.from(fragment.children).some((child) => child.matches('p, ul, ol, blockquote, pre'));
    return blocks && paragraph ? spliceBlockFragment(root, selection, fragment, paragraph) : insertInlineFragment(root, selection, fragment);
}

function paragraphAt(node: Node): HTMLParagraphElement | null {
    return (node instanceof HTMLElement ? node : node.parentElement)?.closest('p') ?? null;
}

function removeEmptyWrapper(element: HTMLElement): void {
    if (!element.textContent && !element.querySelector('[data-message-reference], img')) element.remove();
}

/** Lift an insertion boundary out of inline marks and links, keeping both sides of each wrapper. */
function splitInlineAncestors(caret: Text): void {
    let parent = caret.parentElement;
    while (parent?.matches('strong, b, em, i, ins, u, s, del, a')) {
        const following = caret.ownerDocument.createRange();
        following.selectNodeContents(parent);
        following.setStartAfter(caret);
        const after = parent.cloneNode(false) as HTMLElement;
        after.append(following.extractContents());
        parent.after(caret, after);
        removeEmptyWrapper(parent);
        removeEmptyWrapper(after);
        parent = caret.parentElement;
    }
}

function insertInlineFragment(root: HTMLElement, selection: Range, fragment: DocumentFragment): Range {
    const caret = root.ownerDocument.createTextNode('');
    const range = replaceMessageRange(selection, caret);
    splitInlineAncestors(caret);
    range.setStartBefore(caret);
    range.collapse(true);
    caret.remove();
    return replaceMessageRange(range, fragment);
}

/** Merge the surviving prefix/suffix into the first/last pasted paragraphs around the inserted blocks. */
function spliceBlockFragment(root: HTMLElement, selection: Range, fragment: DocumentFragment, originalParagraph: HTMLParagraphElement): Range {
    const deletionPoint = root.ownerDocument.createTextNode('');
    const range = replaceMessageRange(selection, deletionPoint);
    let paragraph = paragraphAt(range.startContainer);
    if (!paragraph && root.contains(originalParagraph)) {
        // Cross-block deletion may collapse at the host boundary; retain the surviving paragraph's prefix.
        paragraph = originalParagraph;
        range.setStart(paragraph, paragraph.childNodes.length);
        range.collapse(true);
    }
    deletionPoint.remove();
    if (!paragraph) return replaceMessageRange(range, fragment);
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
    const after = last?.matches('p') ? last : root.ownerDocument.createElement('p');
    const caret = root.ownerDocument.createTextNode('');
    after.append(caret, suffix);
    if (after !== last) fragment.append(after);
    paragraph.after(fragment);
    removeEmptyWrapper(paragraph);
    range.setStartBefore(caret);
    range.collapse(true);
    caret.remove();
    return range;
}
