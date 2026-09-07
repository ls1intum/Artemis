/**
 * Snaps a measured CSS-pixel length to the device pixel grid.
 *
 * These measurements position everything the shells render below the header, and `getBoundingClientRect()` reports
 * subpixel sizes. A fractional offset puts every hairline below it across two device rows instead of on one.
 *
 * Rounds against `devicePixelRatio`, not to whole CSS pixels: on a 2x display a whole CSS pixel is still half a
 * device pixel.
 */
function snapToDevicePixelGrid(cssPixels: number): number {
    const ratio = window.devicePixelRatio || 1;
    return Math.round(cssPixels * ratio) / ratio;
}

/**
 * Update the header height SCSS variable based on the navbar height.
 *
 * The navbar height can change based on the screen size and the content of the navbar
 * (e.g. long breadcrumbs due to longs exercise names)
 */
export function updateHeaderHeight() {
    setTimeout(() => {
        const navbar = document.querySelector('jhi-navbar');
        if (navbar) {
            // do not use navbar.offsetHeight, this might not be defined in Safari!
            const headerHeight = snapToDevicePixelGrid(navbar.getBoundingClientRect().height);
            document.documentElement.style.setProperty('--header-height', `${headerHeight}px`);
        }
    });
}

/*
 * Deliberately NOT `--header-height`. That variable means "height of whatever header is currently on top" and is
 * owned by exam mode: `ExamNavigationBarComponent` sets it to the exam navigation bar's height during conduction
 * and `ExamParticipationService.resetExamLayout()` restores it afterwards. Writing the global navbar's height
 * there — or removing it while the navbar is hidden during an exam — fought with that and broke the exam layout
 * after a reload. `--navbar-height` is owned solely by the observer below and always means the global navbar.
 */
const SHELL_METRICS: { selector: string; cssVariable: string }[] = [
    { selector: 'jhi-navbar', cssVariable: '--navbar-height' },
    { selector: 'jhi-footer', cssVariable: '--footer-height' },
];

/** The observer created by the most recent {@link observeShellMetrics} call, or undefined while none is active. */
let activeObserver: ResizeObserver | undefined;

/**
 * Keeps `--navbar-height` and `--footer-height` in sync with the navbar and footer as actually rendered.
 *
 * The shells (student overview, course management, administration) size their content region as
 * `100vh - var(--navbar-height) - var(--footer-height) - var(--spacing-divider)`. That only lands on an exact
 * one-divider gap above the footer while both variables reflect reality — the previous hardcoded
 * `--sidebar-header-footer-combined-height: 88px` under-stated the real ~95px, which is why the gap between
 * the content and the footer drifted from page to page. Both elements resize (breadcrumbs wrap, the footer's
 * dev-only git line wraps), hence a `ResizeObserver` rather than a single measurement.
 *
 * Call {@link reattachShellMetricsObserver} after a navigation that may have re-created the navbar or footer.
 *
 * @returns a teardown function that stops this call's observer. It is idempotent and only clears the shared
 * reference when it still points at its own observer, so a teardown from a superseded caller (component
 * re-creation during HMR, overlapping test fixtures) cannot silently disable a live observer.
 */
export function observeShellMetrics(): () => void {
    if (typeof ResizeObserver === 'undefined') {
        measureShellMetrics();
        return () => {};
    }
    const observer = new ResizeObserver(() => measureShellMetrics());
    activeObserver = observer;
    reattachShellMetricsObserver();
    return () => {
        observer.disconnect();
        if (activeObserver === observer) {
            activeObserver = undefined;
        }
    };
}

/** Points the observer at whichever navbar/footer elements are in the DOM at this moment. */
function observeShellElements(observer: ResizeObserver): void {
    observer.disconnect();
    for (const { selector } of SHELL_METRICS) {
        const element = document.querySelector(selector);
        if (element) {
            observer.observe(element);
        }
    }
}

/** Re-targets the observer at the current navbar/footer elements and re-measures. */
export function reattachShellMetricsObserver(): void {
    const observer = activeObserver;
    if (!observer) {
        return;
    }
    observeShellElements(observer);
    // Deferred like `updateHeaderHeight`: at `NavigationEnd` the newly activated navbar view has not been
    // change-detected yet, so measuring synchronously can write a transient (possibly 0px) height and cause a
    // one-frame layout jump. `observe()` also delivers an initial callback, which corrects any drift.
    //
    // Discovery is repeated here rather than only the measurement. Returning from a skeleton-less route (LTI
    // embedding, standalone problem statement, exam conduction) recreates the navbar and footer, and they are not in
    // the DOM yet during the synchronous pass above — so it observes nothing. Measuring alone would then write the
    // right values once and never update again: a wrapping breadcrumb, an appearing notification banner or a growing
    // footer would leave the shells sized against a stale height until the next navigation.
    setTimeout(() => {
        // A later `observeShellMetrics()` call may have superseded this observer, or it may have been torn down.
        if (activeObserver !== observer) {
            return;
        }
        observeShellElements(observer);
        measureShellMetrics();
    });
}

function measureShellMetrics(): void {
    const root = document.documentElement;
    for (const { selector, cssVariable } of SHELL_METRICS) {
        const element = document.querySelector(selector);
        if (!element) {
            // The navbar/footer are removed on the skeleton-less routes (LTI embedding, standalone problem
            // statement, exam conduction for the footer). Drop the inline value so the theme fallback applies
            // again instead of leaving the last measured height behind.
            root.style.removeProperty(cssVariable);
            continue;
        }
        // do not use offsetHeight, this might not be defined in Safari!
        const next = `${snapToDevicePixelGrid(element.getBoundingClientRect().height)}px`;
        // Writing an unchanged value would invalidate style for the whole document on every navigation.
        if (root.style.getPropertyValue(cssVariable) !== next) {
            root.style.setProperty(cssVariable, next);
        }
    }
}
