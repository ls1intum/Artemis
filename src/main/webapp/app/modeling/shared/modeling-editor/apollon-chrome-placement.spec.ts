import {
    RAIL_DISCLOSURE_MAX_HEIGHT,
    RAIL_DISCLOSURE_MIN_HEIGHT,
    applyBottomCenterPlacement,
    calculateBottomCenterPlacement,
    calculateRailDisclosureMaxHeight,
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
        const placement = calculateBottomCenterPlacement({
            ...baseGeometry,
            palette: rectangle(760, 984),
            paletteRegion: 'right-rail',
        });

        expect(placement.elevated).toBe(false);
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
    });

    it('clears every property owned by the placement helper', () => {
        const element = document.createElement('div');
        applyBottomCenterPlacement(element, '--test-shift', { elevated: false, panelWidth: 620, shift: -10 });

        clearBottomCenterPlacement(element, '--test-shift');

        expect(element.style.getPropertyValue('--test-shift')).toBe('');
        expect(element.style.getPropertyValue('--modeling-editor-explanation-panel-width')).toBe('');
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

describe('calculateRailDisclosureMaxHeight', () => {
    const baseGeometry = {
        root: { left: 0, right: 1000, bottom: 1000 },
        trigger: { right: 900, bottom: 100 },
        panelWidth: 300,
        bottomChrome: [] as Array<{ left: number; right: number; top: number; width: number; height: number } | undefined>,
        chromeGap: 8,
        chromeEdge: 16,
    };
    const chrome = (left: number, right: number, top: number) => ({ left, right, top, width: right - left, height: 40 });

    it('stops the panel a chrome edge short of the canvas floor when nothing is below it', () => {
        expect(calculateRailDisclosureMaxHeight(baseGeometry)).toBe(RAIL_DISCLOSURE_MAX_HEIGHT);
    });

    it('stops a chrome gap short of chrome the panel would otherwise cover', () => {
        const height = calculateRailDisclosureMaxHeight({ ...baseGeometry, bottomChrome: [chrome(700, 950, 600)] });

        expect(height).toBe(600 - 8 - 100);
    });

    it('ignores chrome the panel does not reach across', () => {
        const height = calculateRailDisclosureMaxHeight({ ...baseGeometry, bottomChrome: [chrome(100, 500, 600)] });

        expect(height).toBe(RAIL_DISCLOSURE_MAX_HEIGHT);
    });

    it('ignores collapsed and absent chrome', () => {
        const collapsed = { left: 700, right: 700, top: 600, width: 0, height: 0 };
        const height = calculateRailDisclosureMaxHeight({ ...baseGeometry, bottomChrome: [undefined, collapsed] });

        expect(height).toBe(RAIL_DISCLOSURE_MAX_HEIGHT);
    });

    it('ignores chrome that sits level with or above the trigger', () => {
        const height = calculateRailDisclosureMaxHeight({ ...baseGeometry, bottomChrome: [chrome(700, 950, 100)] });

        expect(height).toBe(RAIL_DISCLOSURE_MAX_HEIGHT);
    });

    it('keeps the panel usable rather than shrinking it to nothing in a cramped canvas', () => {
        const height = calculateRailDisclosureMaxHeight({ ...baseGeometry, bottomChrome: [chrome(700, 950, 150)] });

        expect(height).toBe(RAIL_DISCLOSURE_MIN_HEIGHT);
    });
});
