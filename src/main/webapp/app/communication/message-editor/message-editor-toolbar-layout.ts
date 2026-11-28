/**
 * Counts how many of the leading toolbar items fit one line next to the controls that always stay on it.
 * The rest moves into the bar of further options, so the toolbar never needs a second line.
 *
 * All buttons are icon buttons of one size, and the line holds the items, then the fixed controls, with one gap between neighbours.
 * A toolbar that has not been laid out (zero width, as in a hidden tab) keeps every item, so that nothing is hidden by a measurement that is not there.
 *
 * @param lineWidth the width of the line the buttons share
 * @param buttonWidth the width of one icon button
 * @param gap the space between two buttons
 * @param fixedControls how many buttons stay on the line, such as the emoji and the more options button
 * @param itemCount how many items could be shown
 */
export function visibleToolbarItemCount(lineWidth: number, buttonWidth: number, gap: number, fixedControls: number, itemCount: number): number {
    if (lineWidth <= 0 || buttonWidth <= 0) {
        return itemCount;
    }
    const room = lineWidth - fixedControls * (buttonWidth + gap);
    const fitting = Math.floor((room + gap) / (buttonWidth + gap));
    return Math.max(0, Math.min(itemCount, fitting));
}

/**
 * The width of the toolbar line from which the longer hint below the editor fits two lines.
 * Below it, the editor shows the short one, so that a narrow thread pane needs no more than two lines for the hint either.
 */
export const WIDE_HINT_MIN_WIDTH = 480;

/** Whether the longer hint fits. A toolbar that has not been laid out shows it, like a toolbar that is wide enough. */
export function showsWideHint(lineWidth: number): boolean {
    return lineWidth <= 0 || lineWidth >= WIDE_HINT_MIN_WIDTH;
}

/** The top edge of the closest ancestor that clips or scrolls its children, or of the viewport when there is none. */
export function clippingTop(element: HTMLElement): number {
    for (let parent = element.parentElement; parent; parent = parent.parentElement) {
        if (getComputedStyle(parent).overflowY !== 'visible') {
            return parent.getBoundingClientRect().top;
        }
    }
    return 0;
}

/** The space between the bar and the toolbar, in px. It matches the margin class of the bar. */
export const BAR_OFFSET = 4;

/**
 * Whether a bar of the given height does not fit above the toolbar before it reaches the edge of what its ancestors show.
 * A bar that has no height yet (not laid out) is not placed anywhere else.
 */
export function barNeedsRoomBelow(barHeight: number, toolbarTop: number, clipTop: number): boolean {
    return barHeight > 0 && toolbarTop - clipTop < barHeight + BAR_OFFSET;
}
