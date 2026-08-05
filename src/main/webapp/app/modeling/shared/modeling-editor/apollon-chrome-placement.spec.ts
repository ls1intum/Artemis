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
