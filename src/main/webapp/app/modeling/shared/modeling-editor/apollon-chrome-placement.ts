type Rectangle = Pick<DOMRect, 'left' | 'right' | 'width'>;

export interface BottomCenterPlacementGeometry {
    root: Rectangle;
    zoom: Rectangle;
    minimap: Rectangle;
    surface: Rectangle;
    palette?: Rectangle;
    paletteRegion?: string;
    obstruction?: Rectangle;
    chromeGap: number;
    chromeEdge: number;
    rootFontSize: number;
    previousShift: number;
}

export interface BottomCenterPlacement {
    elevated: boolean;
    panelWidth: number;
    shift: number;
}

const MAXIMUM_SURFACE_WIDTH_REM = 44;
const MINIMUM_INLINE_SURFACE_WIDTH_REM = 12;

/** Uses viewport coordinates; previousShift makes repeated measurements idempotent. */
export function calculateBottomCenterPlacement(geometry: BottomCenterPlacementGeometry): BottomCenterPlacement {
    const { root, zoom, minimap, surface, palette, paletteRegion, obstruction, chromeGap, chromeEdge, rootFontSize, previousShift } = geometry;
    let placementLeft = root.left + chromeEdge;
    let placementRight = root.right - chromeEdge;

    if (palette && paletteRegion === 'left-rail') {
        placementLeft = Math.max(placementLeft, palette.right + chromeGap);
    } else if (palette && paletteRegion === 'right-rail') {
        placementRight = Math.min(placementRight, palette.left - chromeGap);
    }

    const inlineLeft = Math.max(placementLeft, zoom.right + chromeGap);
    const inlineRight = Math.min(placementRight, minimap.left - chromeGap);
    const inlineWidth = Math.max(0, inlineRight - inlineLeft);
    const elevated = inlineWidth < MINIMUM_INLINE_SURFACE_WIDTH_REM * rootFontSize;

    if (!elevated) {
        placementLeft = inlineLeft;
        placementRight = inlineRight;
    } else if (obstruction) {
        placementRight = Math.min(placementRight, obstruction.left - chromeGap);
    }

    const availableWidth = Math.max(0, placementRight - placementLeft);
    const panelWidth = Math.min(MAXIMUM_SURFACE_WIDTH_REM * rootFontSize, availableWidth);
    const editorCenter = root.left + root.width / 2;
    const center = elevated ? placementRight - panelWidth / 2 : Math.min(Math.max(editorCenter, placementLeft + panelWidth / 2), placementRight - panelWidth / 2);
    const shift = center - (surface.left + surface.width / 2 - previousShift);

    return { elevated, panelWidth, shift };
}

export function synchronizeResizeObserverTargets(observer: ResizeObserver, observed: Set<HTMLElement>, targets: Array<HTMLElement | null | undefined>): void {
    const nextTargets = new Set(targets.filter((target): target is HTMLElement => target !== null && target !== undefined));

    for (const element of observed) {
        if (!nextTargets.has(element)) {
            observer.unobserve(element);
            observed.delete(element);
        }
    }
    for (const element of nextTargets) {
        if (!observed.has(element)) {
            observer.observe(element);
            observed.add(element);
        }
    }
}

export function applyBottomCenterPlacement(element: HTMLElement, shiftProperty: string, placement: BottomCenterPlacement): void {
    setPropertyIfChanged(element, shiftProperty, toPixelValue(placement.shift));
    setPropertyIfChanged(element, '--modeling-editor-explanation-panel-width', toPixelValue(placement.panelWidth));
    setPropertyIfChanged(element, '--modeling-editor-explanation-panel-offset-x', '0px');
}

export function clearBottomCenterPlacement(element: HTMLElement | undefined, shiftProperty: string): void {
    if (!element) {
        return;
    }
    for (const property of [shiftProperty, '--modeling-editor-explanation-panel-width', '--modeling-editor-explanation-panel-offset-x']) {
        if (element.style.getPropertyValue(property)) {
            element.style.removeProperty(property);
        }
    }
}

function setPropertyIfChanged(element: HTMLElement, property: string, value: string): void {
    if (element.style.getPropertyValue(property) !== value) {
        element.style.setProperty(property, value);
    }
}

function toPixelValue(value: number): string {
    return `${Math.round(value * 100) / 100}px`;
}
