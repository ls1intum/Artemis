/** Reveals an active option by scrolling only its result container, preserving editor/search focus and ancestor scroll positions. */
export function revealMessageEditorOption(container: HTMLElement, option: HTMLElement): void {
    const viewport = container.getBoundingClientRect();
    const top = viewport.top + container.clientTop;
    // clientHeight rounds fractional sizes; WebKit also rounds assigned scroll offsets to whole pixels.
    const bottom = Math.min(top + container.clientHeight, viewport.bottom);
    const bounds = option.getBoundingClientRect();
    if (bounds.top < top) container.scrollTop = Math.max(0, Math.floor(container.scrollTop + bounds.top - top));
    else if (bounds.bottom > bottom) container.scrollTop = Math.ceil(container.scrollTop + bounds.bottom - bottom);
}
