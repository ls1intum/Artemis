/** Repairs browser layout placeholders and returns a repositioned caret, leaving selection activation to the adapter. */
export function repairMessageCaret(root: HTMLElement, activeRange: Range | undefined, native = false): Range | undefined {
    const caret = activeRange?.collapsed && root.contains(activeRange.commonAncestorContainer) ? activeRange.cloneRange() : undefined;
    let repaired: Range | undefined;
    // Inline parents need placeholders too when a soft break ends a formatted run.
    const containers = new Set([...root.querySelectorAll('p, li, pre > code'), ...Array.from(root.querySelectorAll('br'), (lineBreak) => lineBreak.parentElement!)]);
    for (const container of containers) {
        let placeholder = container.querySelector(':scope > br[data-message-placeholder]');
        const last = lastMeaningfulChild(placeholder?.previousSibling ?? container.lastChild);
        // A browser-created empty paragraph/item already has its layout break. Adding another would create a deletable blank line.
        if (native && last instanceof HTMLBRElement && !placeholder && !container.textContent && container.childNodes.length === 1) {
            last.setAttribute('data-message-placeholder', '');
            continue;
        }
        if (!container.childNodes.length) {
            placeholder = appendPlaceholder(container);
            if (caret && container.contains(caret.startContainer)) repaired = placeCaretBefore(caret, placeholder);
            continue;
        }
        if (!(last instanceof HTMLBRElement) && !last?.textContent?.endsWith('\n')) {
            placeholder?.remove();
            continue;
        }
        placeholder ??= appendPlaceholder(container);
        if (caret && container.contains(caret.startContainer) && atVisibleEnd(container, caret)) {
            // Paint the caret on the new blank line, even if the old boundary preceded an empty typing node (WebKit).
            repaired = placeCaretBefore(caret, placeholder);
        }
    }
    return repaired;
}

function lastMeaningfulChild(node: ChildNode | null): ChildNode | null {
    while (node?.nodeType === Node.TEXT_NODE && !node.textContent) node = node.previousSibling;
    return node;
}

function appendPlaceholder(container: Element): HTMLBRElement {
    const placeholder = container.ownerDocument.createElement('br');
    placeholder.setAttribute('data-message-placeholder', '');
    container.append(placeholder);
    return placeholder;
}

function atVisibleEnd(container: Element, caret: Range): boolean {
    const suffix = caret.cloneRange();
    suffix.selectNodeContents(container);
    suffix.setStart(caret.startContainer, caret.startOffset);
    const remaining = suffix.cloneContents();
    remaining.querySelectorAll('[data-message-placeholder]').forEach((item) => item.remove());
    return !remaining.textContent && !remaining.querySelector('br, img, [data-message-reference]');
}

function placeCaretBefore(caret: Range, placeholder: Element): Range {
    caret.setStartBefore(placeholder);
    caret.collapse(true);
    return caret;
}
