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

export const RAIL_DISCLOSURE_MIN_HEIGHT = 224;
export const RAIL_DISCLOSURE_MAX_HEIGHT = 720;

const MAXIMUM_SURFACE_WIDTH_REM = 44;
const MINIMUM_INLINE_SURFACE_WIDTH_REM = 12;

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
}

export function clearBottomCenterPlacement(element: HTMLElement | undefined, shiftProperty: string): void {
    if (!element) {
        return;
    }
    for (const property of [shiftProperty, '--modeling-editor-explanation-panel-width']) {
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

/**
 * How tall a rail disclosure's panel may grow before it runs into the chrome
 * parked below it (zoom, minimap, the bottom-center surface) or the canvas edge.
 *
 * Only the host can answer this: the disclosure knows nothing about what else
 * the editor has placed on the canvas. Rectangles are viewport coordinates.
 */
export function calculateRailDisclosureMaxHeight(geometry: {
    root: Pick<DOMRect, 'left' | 'right' | 'bottom'>;
    trigger: Pick<DOMRect, 'right' | 'bottom'>;
    panelWidth: number;
    bottomChrome: Array<Pick<DOMRect, 'left' | 'right' | 'top' | 'width' | 'height'> | undefined>;
    chromeGap: number;
    chromeEdge: number;
}): number {
    const { root, trigger, panelWidth, bottomChrome, chromeGap, chromeEdge } = geometry;
    const panelRight = trigger.right;
    const panelLeft = Math.max(root.left, panelRight - panelWidth);
    let boundary = root.bottom - chromeEdge;

    for (const rect of bottomChrome) {
        if (!rect) {
            continue;
        }
        const intersectsHorizontally = panelLeft < rect.right && panelRight > rect.left;
        if (rect.width > 0 && rect.height > 0 && rect.top > trigger.bottom && intersectsHorizontally) {
            boundary = Math.min(boundary, rect.top - chromeGap);
        }
    }

    return Math.max(RAIL_DISCLOSURE_MIN_HEIGHT, Math.min(RAIL_DISCLOSURE_MAX_HEIGHT, Math.floor(boundary - trigger.bottom)));
}

/**
 * Reads the live geometry a rail disclosure sits in and returns the height cap
 * for its panel. Returns the unconstrained maximum while the panel is closed —
 * there is nothing to measure against, and a stale cap would clamp the panel the
 * moment it opens.
 */
export function measureRailDisclosureMaxHeight(
    apollonRoot: HTMLElement | null | undefined,
    disclosure: HTMLElement | null | undefined,
    visible: boolean,
    bottomChrome: Array<HTMLElement | null | undefined>,
): number {
    const trigger = disclosure?.querySelector<HTMLElement>('.apollon-rail-disclosure__trigger-island');
    const panel = disclosure?.querySelector<HTMLElement>('.apollon-rail-disclosure__panel');
    if (!apollonRoot || !trigger || !panel || !visible) {
        return RAIL_DISCLOSURE_MAX_HEIGHT;
    }

    const styles = getComputedStyle(apollonRoot);
    return calculateRailDisclosureMaxHeight({
        root: apollonRoot.getBoundingClientRect(),
        trigger: trigger.getBoundingClientRect(),
        panelWidth: panel.getBoundingClientRect().width,
        bottomChrome: bottomChrome.map((element) => (element && !element.hidden ? element.getBoundingClientRect() : undefined)),
        chromeGap: Number.parseFloat(styles.getPropertyValue('--apollon-chrome-gap')) || 0,
        chromeEdge: Number.parseFloat(styles.getPropertyValue('--apollon-chrome-edge')) || 0,
    });
}
