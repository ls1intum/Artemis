import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { observeShellMetrics, reattachShellMetricsObserver, updateHeaderHeight } from 'app/foundation/util/navbar.util';

/**
 * The shells size their content region from `--navbar-height` / `--footer-height`, so these variables must track
 * the rendered navbar and footer exactly — a stale or missing value silently mis-sizes every page.
 */
describe('navbar util shell metrics', () => {
    const observed = new Set<Element>();
    const originalResizeObserver = globalThis.ResizeObserver;
    let disconnectCount: number;
    let teardowns: (() => void)[];

    /** Adds an element whose `getBoundingClientRect` reports the given height, mirroring a rendered navbar/footer. */
    function addElement(tagName: string, height: number): HTMLElement {
        const element = document.createElement(tagName);
        element.getBoundingClientRect = () => ({ height }) as DOMRect;
        document.body.appendChild(element);
        return element;
    }

    beforeEach(() => {
        vi.useFakeTimers();
        observed.clear();
        disconnectCount = 0;
        teardowns = [];
        document.documentElement.style.removeProperty('--navbar-height');
        document.documentElement.style.removeProperty('--header-height');
        document.documentElement.style.removeProperty('--footer-height');
        document.body.replaceChildren();
        // The global polyfill is a no-op; this one records targets and lets tests trigger the callback.
        globalThis.ResizeObserver = class {
            constructor(private readonly callback: () => void) {
                lastCallback = this.callback;
            }
            observe(target: Element): void {
                observed.add(target);
            }
            unobserve(target: Element): void {
                observed.delete(target);
            }
            disconnect(): void {
                disconnectCount++;
                observed.clear();
            }
        } as unknown as typeof ResizeObserver;
    });

    afterEach(() => {
        teardowns.forEach((teardown) => teardown());
        globalThis.ResizeObserver = originalResizeObserver;
        vi.useRealTimers();
    });

    let lastCallback: (() => void) | undefined;

    /** Starts observing and registers the teardown so a failing expectation cannot leak an observer. */
    function startObserving(): () => void {
        const teardown = observeShellMetrics();
        teardowns.push(teardown);
        return teardown;
    }

    it('writes the measured navbar and footer heights', () => {
        addElement('jhi-navbar', 63.75);
        addElement('jhi-footer', 31.5);

        startObserving();
        vi.runAllTimers();

        expect(document.documentElement.style.getPropertyValue('--navbar-height')).toBe('63.75px');
        expect(document.documentElement.style.getPropertyValue('--footer-height')).toBe('31.5px');
    });

    it('observes both elements and re-targets them after a navigation', () => {
        const navbar = addElement('jhi-navbar', 60);
        addElement('jhi-footer', 30);

        startObserving();
        expect(observed.size).toBe(2);

        // A navigation replaces the navbar element; the observer must follow the new one.
        navbar.remove();
        const replacement = addElement('jhi-navbar', 90);
        reattachShellMetricsObserver();
        vi.runAllTimers();

        expect(observed.has(replacement)).toBe(true);
        expect(document.documentElement.style.getPropertyValue('--navbar-height')).toBe('90px');
    });

    it('clears the variable when the element is gone so the theme fallback applies again', () => {
        const navbar = addElement('jhi-navbar', 60);
        addElement('jhi-footer', 30);
        startObserving();
        vi.runAllTimers();
        expect(document.documentElement.style.getPropertyValue('--navbar-height')).toBe('60px');

        // The skeleton-less routes (LTI embedding, standalone problem statement) remove the navbar entirely.
        navbar.remove();
        reattachShellMetricsObserver();
        vi.runAllTimers();

        expect(document.documentElement.style.getPropertyValue('--navbar-height')).toBe('');
        expect(document.documentElement.style.getPropertyValue('--footer-height')).toBe('30px');
    });

    it('re-measures when the observer callback fires', () => {
        const navbar = addElement('jhi-navbar', 60);
        startObserving();
        vi.runAllTimers();
        expect(document.documentElement.style.getPropertyValue('--navbar-height')).toBe('60px');

        // Breadcrumbs wrapping makes the navbar taller without any navigation.
        navbar.getBoundingClientRect = () => ({ height: 88 }) as DOMRect;
        lastCallback!();

        expect(document.documentElement.style.getPropertyValue('--navbar-height')).toBe('88px');
    });

    it('does not rewrite an unchanged value', () => {
        addElement('jhi-navbar', 60);
        startObserving();
        vi.runAllTimers();

        const setProperty = vi.spyOn(document.documentElement.style, 'setProperty');
        lastCallback!();

        expect(setProperty).not.toHaveBeenCalled();
        setProperty.mockRestore();
    });

    it('keeps a live observer working when a superseded caller tears down', () => {
        addElement('jhi-navbar', 60);
        const staleTeardown = startObserving();
        startObserving();

        // The first AppComponent is destroyed after the replacement has started observing (HMR, overlapping fixtures).
        staleTeardown();
        const disconnectsBefore = disconnectCount;
        reattachShellMetricsObserver();

        // Still active: reattach re-targets rather than silently no-op'ing.
        expect(disconnectCount).toBeGreaterThan(disconnectsBefore);
        expect(observed.size).toBe(1);
    });

    it('is safe to tear down twice', () => {
        addElement('jhi-navbar', 60);
        const teardown = startObserving();

        expect(() => {
            teardown();
            teardown();
        }).not.toThrow();
    });

    it('is a no-op while no observer is active', () => {
        addElement('jhi-navbar', 60);

        // `NavigationEnd` can fire before the app component starts observing, and again after teardown.
        expect(() => reattachShellMetricsObserver()).not.toThrow();
        vi.runAllTimers();

        expect(document.documentElement.style.getPropertyValue('--navbar-height')).toBe('');
        expect(observed.size).toBe(0);
    });

    it('still measures once when ResizeObserver is unavailable', () => {
        addElement('jhi-navbar', 72);
        (globalThis as { ResizeObserver?: unknown }).ResizeObserver = undefined;

        const teardown = observeShellMetrics();

        expect(document.documentElement.style.getPropertyValue('--navbar-height')).toBe('72px');
        expect(() => teardown()).not.toThrow();
    });

    it('updateHeaderHeight writes --header-height, which exam mode owns, not --navbar-height', () => {
        addElement('jhi-navbar', 44);

        updateHeaderHeight();
        expect(document.documentElement.style.getPropertyValue('--header-height')).toBe('');

        vi.runAllTimers();
        expect(document.documentElement.style.getPropertyValue('--header-height')).toBe('44px');
        // The observer must never touch --header-height: the exam navigation bar repurposes it during conduction.
        expect(document.documentElement.style.getPropertyValue('--navbar-height')).toBe('');
    });
});
