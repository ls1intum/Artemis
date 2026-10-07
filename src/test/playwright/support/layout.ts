import { Locator, Page, errors, expect, test } from '@playwright/test';

/**
 * Helpers for layout contract tests: assertions about the geometry of rendered elements (their size, their alignment with each other,
 * whether they fill their parent, whether a page scrolls sideways) instead of screenshots.
 * <p>
 * The helpers measure in the browser, retry until the layout has settled (a client that is still reacting to a resize or to a
 * navigation is not a failure), and fail with a message that names the element, the viewport and the numbers they found. A violated
 * expectation is retried for {@link LayoutOptions.timeout} before it fails, so a test that is meant to fail costs that long.
 */

/** The edges of a box, measured from the top left corner of the viewport. */
type BoxEdge = 'top' | 'right' | 'bottom' | 'left';

/** What {@link expectAligned} compares: an edge of the boxes, or their size. */
type BoxProperty = BoxEdge | 'width' | 'height';

/** The border box of an element in viewport coordinates, in px. */
interface LayoutBox {
    top: number;
    right: number;
    bottom: number;
    left: number;
    width: number;
    height: number;
}

export interface LayoutViewport {
    readonly width: number;
    readonly height: number;
}

interface LayoutOptions {
    /** Names the element in failure messages instead of its selector, e.g. `title row of the quiz page`. */
    name?: string;
    /** The largest accepted difference in px. Sub-pixel layout rounds differently from one engine to the next, so it defaults to half a pixel. */
    tolerance?: number;
    /** How long a violated expectation is retried, in ms, while the client lays out. It defaults to the expect timeout of the project. */
    timeout?: number;
}

/**
 * The viewports a layout contract holds at: a large and a small laptop, a tablet in landscape (just below the 1200px breakpoint at which
 * two column layouts stack) and in portrait, and a phone.
 */
export const LAYOUT_VIEWPORTS: readonly LayoutViewport[] = [
    { width: 1728, height: 1000 },
    { width: 1280, height: 800 },
    { width: 1024, height: 768 },
    { width: 800, height: 700 },
    { width: 480, height: 800 },
];

const DEFAULT_TOLERANCE = 0.5;

/** The window the project gives every `expect` (see `playwright.config.ts`), so a layout check is not stricter than the other assertions on a loaded runner. */
const DEFAULT_TIMEOUT = Number(process.env.EXPECT_TIMEOUT_MS) || 10_000;

/**
 * How long one attempt of a retried check may look for its element: half of the window. An element that never comes then ends the
 * first attempt with a message that names it, and the window ends with that message instead of a bare timeout.
 */
function lookupTimeout(options: LayoutOptions): number {
    return Math.floor((options.timeout ?? DEFAULT_TIMEOUT) / 2);
}

/**
 * Runs the check at every viewport of {@link LAYOUT_VIEWPORTS}, each as a `test.step` of its own, and puts the viewport back to what
 * it was afterwards. The check starts once the viewport is applied and the page has had two identical frames; whatever the client
 * still re-lays out after that is covered by the retry of the assertions.
 */
export async function forEachViewport(page: Page, check: (viewport: LayoutViewport) => Promise<void>): Promise<void> {
    const original = page.viewportSize();
    try {
        for (const viewport of LAYOUT_VIEWPORTS) {
            await test.step(`Viewport ${viewport.width}x${viewport.height}`, async () => {
                await page.setViewportSize(viewport);
                await expect.poll(() => page.evaluate(() => window.innerWidth), { message: `the page should be ${viewport.width}px wide` }).toBe(viewport.width);
                await measure(page.locator('body'));
                await check(viewport);
            });
        }
    } finally {
        if (original) {
            await page.setViewportSize(original);
        }
    }
}

/**
 * Measures the border box of the element once it has settled: the box is read on consecutive frames until two of them agree, so an
 * element that is still moving is not measured halfway. Fails when the element is not there or not rendered (`display: none`).
 * Use it to record a position, e.g. to compare it between pages with {@link expectSameValue}.
 */
export async function measure(locator: Locator, options: LayoutOptions = {}): Promise<LayoutBox> {
    const timeout = options.timeout ?? DEFAULT_TIMEOUT;
    let box: LayoutBox | null;
    try {
        box = await locator.evaluate(readSettledBox, undefined, { timeout });
    } catch (error) {
        if (error instanceof errors.TimeoutError) {
            throw new Error(`${label(locator, options)}: no element found within ${timeout} ms`, { cause: error });
        }
        throw error;
    }
    if (!box) {
        throw new Error(`${label(locator, options)}: the element is not rendered (display: none)`);
    }
    return box;
}

/** Expects the element to be `expected` px high, within the tolerance. */
export async function expectHeight(locator: Locator, expected: number, options: LayoutOptions = {}): Promise<void> {
    const tolerance = options.tolerance ?? DEFAULT_TOLERANCE;
    await retry(async () => {
        const actual = (await measure(locator, { name: options.name, timeout: lookupTimeout(options) })).height;
        if (Math.abs(actual - expected) > tolerance) {
            throw new Error(`${label(locator, options)}${at(locator.page())}: height is ${px(actual)}, expected ${px(expected)} (tolerance ${px(tolerance)})`);
        }
    }, options.timeout);
}

/**
 * Expects the elements to agree in one edge (`bottom`: they end on the same line) or in one size (`height`), within the tolerance.
 * `options.name` names the group, e.g. `the sidebar card and the content card`. At least two elements are needed, otherwise there is
 * nothing to compare and the test would pass without checking.
 */
export async function expectAligned(locators: readonly Locator[], property: BoxProperty, options: LayoutOptions = {}): Promise<void> {
    if (locators.length < 2) {
        throw new Error(`${options.name ?? 'expectAligned'}: got ${locators.length} element(s), at least 2 are needed to compare them`);
    }
    const tolerance = options.tolerance ?? DEFAULT_TOLERANCE;
    await retry(async () => {
        const boxes = await Promise.all(locators.map((locator) => measure(locator, { timeout: lookupTimeout(options) })));
        const values = boxes.map((box) => box[property]);
        if (Math.max(...values) - Math.min(...values) > tolerance) {
            const found = locators.map((locator, index) => `${String(locator)} ${px(values[index])}`).join(', ');
            throw new Error(`${options.name ?? `${locators.length} elements`}${at(locators[0].page())}: ${property} is not the same: ${found} (tolerance ${px(tolerance)})`);
        }
    }, options.timeout);
}

/**
 * Expects the child to reach the given edges of its parent, within the tolerance: a scroller that ends at the bottom of its card, a
 * column that spans the card. Without edges all four are checked, and an empty list of edges is refused because nothing would be
 * checked. The edges are those of the border boxes, so padding of the parent counts as space the child does not fill.
 */
export async function expectFillsParent(
    child: Locator,
    parent: Locator,
    edges: readonly BoxEdge[] = ['top', 'right', 'bottom', 'left'],
    options: LayoutOptions = {},
): Promise<void> {
    if (edges.length === 0) {
        throw new Error(`${label(child, options)}: no edges to compare with ${String(parent)}`);
    }
    const tolerance = options.tolerance ?? DEFAULT_TOLERANCE;
    await retry(async () => {
        const lookup = lookupTimeout(options);
        const [childBox, parentBox] = await Promise.all([measure(child, { name: options.name, timeout: lookup }), measure(parent, { timeout: lookup })]);
        const gaps = edges
            .map((edge) => {
                // Positive when the child sticks out of the parent at this edge, negative when it stops short of it.
                const outward = edge === 'bottom' || edge === 'right' ? childBox[edge] - parentBox[edge] : parentBox[edge] - childBox[edge];
                return Math.abs(outward) > tolerance
                    ? `${edge} at ${px(childBox[edge])} but its parent's at ${px(parentBox[edge])} (${px(Math.abs(outward))} ${outward < 0 ? 'short' : 'beyond'})`
                    : undefined;
            })
            .filter((gap) => gap !== undefined);
        if (gaps.length > 0) {
            throw new Error(`${label(child, options)}${at(child.page())}: does not fill ${String(parent)}: ${gaps.join(', ')}`);
        }
    }, options.timeout);
}

/**
 * Expects the element not to reach beyond the given edges of the window, within the tolerance: a scroller that ends above the bottom
 * of the window instead of growing with its content. It catches what {@link expectFillsParent} cannot, a parent that grows with the
 * child because the heights between them lost their definite value.
 */
export async function expectWithinViewport(locator: Locator, edges: readonly BoxEdge[], options: LayoutOptions = {}): Promise<void> {
    if (edges.length === 0) {
        throw new Error(`${label(locator, options)}: no edges to compare with the window`);
    }
    const tolerance = options.tolerance ?? DEFAULT_TOLERANCE;
    await retry(async () => {
        const box = await measure(locator, { name: options.name, timeout: lookupTimeout(options) });
        const { width, height } = await locator.page().evaluate(() => ({ width: window.innerWidth, height: window.innerHeight }));
        const limits = { top: 0, right: width, bottom: height, left: 0 };
        const beyond = edges
            .map((edge) => {
                const outward = edge === 'bottom' || edge === 'right' ? box[edge] - limits[edge] : limits[edge] - box[edge];
                return outward > tolerance ? `${edge} at ${px(box[edge])} but the window's at ${px(limits[edge])} (${px(outward)} beyond)` : undefined;
            })
            .filter((gap) => gap !== undefined);
        if (beyond.length > 0) {
            throw new Error(`${label(locator, options)}${at(locator.page())}: reaches beyond the window: ${beyond.join(', ')}`);
        }
    }, options.timeout);
}

/**
 * Compares values that were recorded one by one, such as the top of the title row on every page of a flow: where a position
 * has to stay the same while the page changes, the elements are never on screen together to be compared by {@link expectAligned}.
 * At least two values are needed, otherwise there is nothing to compare and the test would pass without checking.
 */
export function expectSameValue(page: Page, what: string, valuesByName: Record<string, number>, options: LayoutOptions = {}): void {
    const tolerance = options.tolerance ?? DEFAULT_TOLERANCE;
    const entries = Object.entries(valuesByName);
    if (entries.length < 2) {
        throw new Error(`${what}${at(page)}: got ${entries.length} value(s), at least 2 are needed to compare them`);
    }
    const values = entries.map(([, value]) => value);
    const found = entries.map(([name, value]) => `${name} ${px(value)}`).join(', ');
    expect(Math.max(...values) - Math.min(...values), `${what}${at(page)} is not the same: ${found} (tolerance ${px(tolerance)})`).toBeLessThanOrEqual(tolerance);
}

/**
 * Expects the page not to scroll sideways: neither the document nor any of the given scroll containers (the ones that scroll
 * vertically as well and must not show a horizontal scroll bar) is wider than its box. The failure names the elements that stick out,
 * leaving out those that an ancestor of their own clips or scrolls, such as a table in a wrapper that is meant to scroll. The browser
 * rounds `scrollWidth` and `clientWidth` to whole pixels, so a fractional layout reads one pixel wider than its box without anything
 * sticking out; one pixel is therefore not counted, and two are.
 */
export async function expectNoHorizontalOverflow(page: Page, scrollers: readonly Locator[] = [], options: LayoutOptions = {}): Promise<void> {
    await retry(async () => {
        const problems: string[] = [];
        const documentOverflow = await page.evaluate(findHorizontalOverflow, null);
        if (documentOverflow.scrollWidth > documentOverflow.clientWidth + 1) {
            problems.push(`the document is ${px(documentOverflow.scrollWidth)} wide in a ${px(documentOverflow.clientWidth)} viewport${sticking(documentOverflow.offenders)}`);
        }
        for (const scroller of scrollers) {
            // A scroller that is missing or not rendered has no overflow to read, so it must not pass for one that fits.
            await measure(scroller, { timeout: lookupTimeout(options) });
            const scrollerOverflow = await scroller.evaluate(findHorizontalOverflow);
            if (scrollerOverflow.scrollWidth > scrollerOverflow.clientWidth + 1) {
                problems.push(
                    `${String(scroller)} is ${px(scrollerOverflow.scrollWidth)} wide in a ${px(scrollerOverflow.clientWidth)} box${sticking(scrollerOverflow.offenders)}`,
                );
            }
        }
        if (problems.length > 0) {
            throw new Error(`horizontal overflow${at(page)}: ${problems.join('; ')}`);
        }
    }, options.timeout);
}

/**
 * Expects none of the scroll containers the element sits in to scroll sideways. A page that is too wide does not always widen the
 * document: when the page lives in a card that scrolls on its own, the card scrolls sideways and the document stays as wide as the
 * window. The containers are the ancestors with `overflow-x: auto` or `scroll`, found from the element, so a test needs no test id
 * for the container of every page. Scroll containers inside the element, such as a wrapper around a wide table, are not looked at.
 */
export async function expectNoHorizontalScrollAround(locator: Locator, options: LayoutOptions = {}): Promise<void> {
    const ancestors = locator.locator('xpath=ancestor::*');
    // Without the element there are no ancestors to look at, and the check would pass without checking anything.
    await measure(locator, options);
    await retry(async () => {
        const scrolling = await ancestors.evaluateAll((nodes) => nodes.flatMap((node, index) => (['auto', 'scroll'].includes(getComputedStyle(node).overflowX) ? [index] : [])));
        const problems: string[] = [];
        for (const index of scrolling) {
            const overflow = await ancestors.nth(index).evaluate(findHorizontalOverflow);
            if (overflow.scrollWidth > overflow.clientWidth + 1) {
                problems.push(`${overflow.description} is ${px(overflow.scrollWidth)} wide in a ${px(overflow.clientWidth)} box${sticking(overflow.offenders)}`);
            }
        }
        if (problems.length > 0) {
            throw new Error(`${label(locator, options)}${at(locator.page())}: sits in a container that scrolls sideways: ${problems.join('; ')}`);
        }
    }, options.timeout);
}

/** Expects the computed value of a CSS property, e.g. `font-size` `18px`. Colours and lengths are compared as the browser computes them. */
export async function expectComputedStyle(locator: Locator, property: string, value: string, options: LayoutOptions = {}): Promise<void> {
    await expect(locator, `${label(locator, options)}${at(locator.page())}: computed ${property}`).toHaveCSS(property, value, { timeout: options.timeout });
}

/**
 * Expects all elements the locator matches to have the same computed value of every given CSS property, e.g. headings that have to
 * look alike. At least two elements have to match, otherwise there is nothing to compare and the test would pass without checking.
 */
export async function expectSameComputedStyle(locator: Locator, properties: readonly string[], options: LayoutOptions = {}): Promise<void> {
    await retry(async () => {
        const elements = await locator.evaluateAll(
            (nodes, names) =>
                nodes.map((node) => {
                    const style = getComputedStyle(node);
                    return { text: (node.textContent ?? '').trim().slice(0, 40), values: names.map((name) => style.getPropertyValue(name)) };
                }),
            [...properties],
        );
        const where = `${label(locator, options)}${at(locator.page())}`;
        if (elements.length < 2) {
            throw new Error(`${where}: found ${elements.length} element(s), at least 2 are needed to compare them`);
        }
        for (const [index, property] of properties.entries()) {
            if (new Set(elements.map((element) => element.values[index])).size > 1) {
                throw new Error(`${where}: ${property} is not the same: ${elements.map((element) => `'${element.text}' ${element.values[index]}`).join(', ')}`);
            }
        }
    }, options.timeout);
}

/** Runs the check, which throws what is wrong with the layout, until it passes, and throws the last problem it found when the time is up. */
async function retry(check: () => Promise<void>, timeout = DEFAULT_TIMEOUT): Promise<void> {
    await expect(check).toPass({ timeout, intervals: [50, 100, 250, 500] });
}

function label(locator: Locator, options: LayoutOptions): string {
    return options.name ?? String(locator);
}

/** The viewport for a failure message: ` at 1280x800`. */
function at(page: Page): string {
    const viewport = page.viewportSize();
    return viewport ? ` at ${viewport.width}x${viewport.height}` : '';
}

function px(value: number): string {
    return `${Math.round(value * 10) / 10}px`;
}

function sticking(offenders: string[]): string {
    return offenders.length > 0 ? `, sticking out: ${offenders.join(', ')}` : '';
}

/** Runs in the browser, so it must not use anything of this module. */
function readSettledBox(element: Element): Promise<LayoutBox | null> {
    const read = () => {
        if (element.getClientRects().length === 0) {
            return null;
        }
        const { top, right, bottom, left, width, height } = element.getBoundingClientRect();
        return { top, right, bottom, left, width, height };
    };
    // A frame that does not come (a page in the background) must not stall the measurement.
    const nextFrame = (callback: () => void) => {
        let called = false;
        const once = () => {
            if (!called) {
                called = true;
                callback();
            }
        };
        requestAnimationFrame(once);
        setTimeout(once, 50);
    };
    return new Promise((resolve) => {
        let previous = JSON.stringify(read());
        let changes = 0;
        const tick = () => {
            const current = JSON.stringify(read());
            if (current === previous || ++changes >= 30) {
                resolve(JSON.parse(current));
                return;
            }
            previous = current;
            nextFrame(tick);
        };
        nextFrame(tick);
    });
}

/**
 * Runs in the browser, so it must not use anything of this module. Without a root it looks at the document. Whether the box scrolls
 * sideways is what the browser says; the elements that stick out of it are only listed to name the cause.
 */
function findHorizontalOverflow(root: Element | null): { description: string; clientWidth: number; scrollWidth: number; offenders: string[] } {
    const describeElement = (element: Element) => {
        const testId = element.getAttribute('data-testid');
        const firstClass = typeof element.className === 'string' ? element.className.trim().split(/\s+/)[0] : '';
        return element.tagName.toLowerCase() + (testId ? `[data-testid=${testId}]` : element.id ? `#${element.id}` : firstClass ? `.${firstClass}` : '');
    };
    const container = root ?? document.documentElement;
    const description = root ? describeElement(root) : 'the document';
    const clientWidth = container.clientWidth;
    const scrollWidth = container.scrollWidth;
    if (scrollWidth <= clientWidth + 1) {
        return { description, clientWidth, scrollWidth, offenders: [] };
    }
    const right = (root ? root.getBoundingClientRect().left + root.clientLeft : 0) + clientWidth;
    const clips = (element: Element) => ['hidden', 'clip', 'auto', 'scroll'].includes(getComputedStyle(element).overflowX);
    const found: { description: string; overshoot: number }[] = [];
    for (const element of Array.from((root ?? document.body).querySelectorAll('*'))) {
        const rect = element.getBoundingClientRect();
        const overshoot = rect.right - right;
        if (overshoot <= 1 || rect.width === 0 || rect.height === 0 || getComputedStyle(element).visibility === 'hidden') {
            continue;
        }
        let exempt = false;
        for (let ancestor = element.parentElement; ancestor && ancestor !== container && ancestor !== document.body; ancestor = ancestor.parentElement) {
            // Fixed elements do not widen the document; whatever an ancestor clips or scrolls is that ancestor's to answer for.
            if (clips(ancestor) || getComputedStyle(ancestor).position === 'fixed') {
                exempt = true;
                break;
            }
        }
        if (!exempt && getComputedStyle(element).position !== 'fixed') {
            found.push({ description: describeElement(element), overshoot });
        }
    }
    found.sort((a, b) => b.overshoot - a.overshoot);
    return { description, clientWidth, scrollWidth, offenders: found.slice(0, 5).map((entry) => `${entry.description} (${Math.round(entry.overshoot)}px beyond the right edge)`) };
}
