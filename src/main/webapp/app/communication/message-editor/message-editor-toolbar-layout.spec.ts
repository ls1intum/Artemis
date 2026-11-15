import { afterEach, describe, expect, it, vi } from 'vitest';
import { BAR_OFFSET, WIDE_HINT_MIN_WIDTH, barNeedsRoomBelow, clippingTop, showsWideHint, visibleToolbarItemCount } from './message-editor-toolbar-layout';

describe('visibleToolbarItemCount', () => {
    // Buttons are 32 px wide with 4 px between them, and the emoji and the more options button always stay on the line.
    const width = 32;
    const gap = 4;
    const fixed = 2;
    const items = 8;
    /** The width of a line that holds exactly this many buttons. */
    const line = (buttons: number) => buttons * width + (buttons - 1) * gap;

    it('keeps every item when the line holds all of them and the fixed controls', () => {
        expect(visibleToolbarItemCount(line(items + fixed), width, gap, fixed, items)).toBe(items);
        expect(visibleToolbarItemCount(line(items + fixed) + 500, width, gap, fixed, items)).toBe(items);
    });

    it('moves the items that do not fit and counts a partial button as missing', () => {
        expect(visibleToolbarItemCount(line(items + fixed) - 1, width, gap, fixed, items)).toBe(items - 1);
        expect(visibleToolbarItemCount(line(6 + fixed), width, gap, fixed, items)).toBe(6);
        expect(visibleToolbarItemCount(line(1 + fixed), width, gap, fixed, items)).toBe(1);
    });

    it('keeps no item when only the fixed controls fit', () => {
        expect(visibleToolbarItemCount(line(fixed), width, gap, fixed, items)).toBe(0);
        expect(visibleToolbarItemCount(10, width, gap, fixed, items)).toBe(0);
    });

    it('keeps every item while the toolbar has no layout', () => {
        expect(visibleToolbarItemCount(0, width, gap, fixed, items)).toBe(items);
        expect(visibleToolbarItemCount(200, 0, gap, fixed, items)).toBe(items);
    });
});

describe('showsWideHint', () => {
    it('shows the longer hint from the width on that it fits two lines, and while the toolbar has no width', () => {
        expect(showsWideHint(WIDE_HINT_MIN_WIDTH)).toBe(true);
        expect(showsWideHint(WIDE_HINT_MIN_WIDTH + 300)).toBe(true);
        expect(showsWideHint(WIDE_HINT_MIN_WIDTH - 1)).toBe(false);
        expect(showsWideHint(212)).toBe(false);
        expect(showsWideHint(0)).toBe(true);
    });
});

describe('barNeedsRoomBelow', () => {
    it('keeps the bar above the toolbar while it fits between the toolbar and the edge of what the ancestors show', () => {
        expect(barNeedsRoomBelow(40, 200, 0)).toBe(false);
        expect(barNeedsRoomBelow(40, 40 + BAR_OFFSET, 0)).toBe(false);
    });

    it('moves the bar below the toolbar when it would reach past that edge', () => {
        expect(barNeedsRoomBelow(86, 64, 0)).toBe(true);
        expect(barNeedsRoomBelow(40, 40 + BAR_OFFSET - 1, 0)).toBe(true);
        // the scroll container starts further down the page
        expect(barNeedsRoomBelow(40, 300, 280)).toBe(true);
    });

    it('does not place a bar that has no height yet anywhere else', () => {
        expect(barNeedsRoomBelow(0, 0, 0)).toBe(false);
    });
});

describe('clippingTop', () => {
    afterEach(() => {
        document.body.replaceChildren();
    });

    function nest(overflowY: string) {
        const scroller = document.createElement('div');
        scroller.style.overflowY = overflowY;
        const wrapper = document.createElement('div');
        const bar = document.createElement('div');
        wrapper.append(bar);
        scroller.append(wrapper);
        document.body.append(scroller);
        vi.spyOn(scroller, 'getBoundingClientRect').mockReturnValue({ top: 120 } as DOMRect);
        return bar;
    }

    it.each(['auto', 'scroll', 'hidden'])('uses the top of the closest ancestor that has overflow %s', (overflowY) => {
        expect(clippingTop(nest(overflowY))).toBe(120);
    });

    it('uses the top of the viewport when no ancestor clips', () => {
        expect(clippingTop(nest('visible'))).toBe(0);
    });
});
