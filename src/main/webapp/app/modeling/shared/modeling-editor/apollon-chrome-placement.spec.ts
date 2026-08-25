import {
    applyBottomCenterPlacement,
    calculateBottomCenterPlacement,
    clearBottomCenterPlacement,
    synchronizeResizeObserverTargets,
} from 'app/modeling/shared/modeling-editor/apollon-chrome-placement';

const rectangle = (left: number, right: number): Pick<DOMRect, 'left' | 'right' | 'width'> => ({ left, right, width: right - left });

describe('calculateBottomCenterPlacement', () => {
    const baseGeometry = {
        root: rectangle(0, 1000),
        zoom: rectangle(16, 116),
        minimap: rectangle(884, 984),
        surface: rectangle(300, 700),
        chromeGap: 8,
        chromeEdge: 16,
        rootFontSize: 16,
        previousShift: 0,
    };

    it('centers the surface between native controls when enough space is available', () => {
        expect(calculateBottomCenterPlacement(baseGeometry)).toEqual({ elevated: false, panelWidth: 704, shift: 0 });
    });

    it('moves the surface above the native row and avoids a visible obstruction', () => {
        const placement = calculateBottomCenterPlacement({
            ...baseGeometry,
            zoom: rectangle(16, 430),
            minimap: rectangle(570, 984),
            obstruction: rectangle(760, 984),
        });

        expect(placement).toEqual({ elevated: true, panelWidth: 704, shift: -100 });
    });

    it('accounts for a palette occupying a side rail', () => {
        const placement = calculateBottomCenterPlacement({
            ...baseGeometry,
            palette: rectangle(16, 240),
            paletteRegion: 'left-rail',
        });

        expect(placement).toEqual({ elevated: false, panelWidth: 628, shift: 62 });
    });

    it('keeps clear of a palette in the right rail, mirroring the left-rail case', () => {
        // The zoom/minimap row still leaves room, so the surface stays inline and is bounded by the palette's left
        // edge rather than by the editor's right inset.
        const placement = calculateBottomCenterPlacement({
            ...baseGeometry,
            palette: rectangle(760, 984),
            paletteRegion: 'right-rail',
        });

        expect(placement.elevated).toBe(false);
        // Right bound is min(minimap.left - gap, palette.left - gap) = min(876, 752) = 752; left bound is zoom.right + gap = 124.
        expect(placement.panelWidth).toBe(628);
        expect(placement.shift).toBe(-62);
    });

    it('lets the palette push the elevated surface off the right edge', () => {
        const placement = calculateBottomCenterPlacement({
            ...baseGeometry,
            zoom: rectangle(16, 430),
            minimap: rectangle(570, 984),
            palette: rectangle(700, 984),
            paletteRegion: 'right-rail',
        });

        // Elevated, so the surface hangs off the right bound, which the palette has pulled in from the editor's own
        // inset (984) to palette.left - gap = 692. Width is then 692 - 16 = 676, capped by nothing.
        expect(placement.elevated).toBe(true);
        expect(placement.panelWidth).toBe(676);
        expect(placement.shift).toBe(-146);
    });
});

describe('bottom-center placement DOM updates', () => {
    it('writes the complete placement once and does not rewrite unchanged values', () => {
        const element = document.createElement('div');
        const setProperty = vi.spyOn(element.style, 'setProperty');
        const placement = { elevated: false, panelWidth: 620.125, shift: -10.126 };

        applyBottomCenterPlacement(element, '--test-shift', placement);
        const initialWriteCount = setProperty.mock.calls.length;
        applyBottomCenterPlacement(element, '--test-shift', placement);

        expect(setProperty).toHaveBeenCalledTimes(initialWriteCount);
        expect(element.style.getPropertyValue('--test-shift')).toBe('-10.13px');
        expect(element.style.getPropertyValue('--modeling-editor-explanation-panel-width')).toBe('620.13px');
        expect(element.style.getPropertyValue('--modeling-editor-explanation-panel-offset-x')).toBe('0px');
    });

    it('clears every property owned by the placement helper', () => {
        const element = document.createElement('div');
        applyBottomCenterPlacement(element, '--test-shift', { elevated: false, panelWidth: 620, shift: -10 });

        clearBottomCenterPlacement(element, '--test-shift');

        expect(element.style.getPropertyValue('--test-shift')).toBe('');
        expect(element.style.getPropertyValue('--modeling-editor-explanation-panel-width')).toBe('');
        expect(element.style.getPropertyValue('--modeling-editor-explanation-panel-offset-x')).toBe('');
    });

    it('observes new targets, unobserves removed targets, and leaves retained targets alone', () => {
        const first = document.createElement('div');
        const second = document.createElement('div');
        const third = document.createElement('div');
        const observed = new Set<HTMLElement>([first, second]);
        const observer = {
            observe: vi.fn(),
            unobserve: vi.fn(),
        } as unknown as ResizeObserver;

        synchronizeResizeObserverTargets(observer, observed, [second, third, third, undefined]);

        expect(observer.unobserve).toHaveBeenCalledExactlyOnceWith(first);
        expect(observer.observe).toHaveBeenCalledExactlyOnceWith(third);
        expect([...observed]).toEqual([second, third]);
    });
});
