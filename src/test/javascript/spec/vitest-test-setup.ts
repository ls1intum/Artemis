/**
 * Global Vitest Test Setup for Artemis
 *
 * Parallel to jest-test-setup.ts, this provides global mocks for Vitest tests.
 * NOTE: monaco-editor is mocked via path alias in vitest.config.ts.
 */
import '@angular/compiler';
import '@angular/localize/init';
import '@analogjs/vitest-angular/setup-snapshots';
import { vi } from 'vitest';

import 'app/shared/util/array.extension';

import dayjs from 'dayjs/esm';
import relativeTime from 'dayjs/esm/plugin/relativeTime';
import localizedFormat from 'dayjs/esm/plugin/localizedFormat';
import utc from 'dayjs/esm/plugin/utc';
import timezone from 'dayjs/esm/plugin/timezone';
import isBetween from 'dayjs/esm/plugin/isBetween';
import isSameOrBefore from 'dayjs/esm/plugin/isSameOrBefore';
import isSameOrAfter from 'dayjs/esm/plugin/isSameOrAfter';
import minMax from 'dayjs/esm/plugin/minMax';
import customParseFormat from 'dayjs/esm/plugin/customParseFormat';
import duration from 'dayjs/esm/plugin/duration';

dayjs.extend(relativeTime);
dayjs.extend(localizedFormat);
dayjs.extend(utc);
dayjs.extend(timezone);
dayjs.extend(isBetween);
dayjs.extend(isSameOrBefore);
dayjs.extend(isSameOrAfter);
dayjs.extend(minMax);
dayjs.extend(customParseFormat);
dayjs.extend(duration);

// Browser API mocks (not available in jsdom)
globalThis.ResizeObserver = class ResizeObserver {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
};

globalThis.IntersectionObserver = class IntersectionObserver {
    readonly root = null;
    readonly rootMargin = '';
    readonly thresholds: readonly number[] = [];
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
    takeRecords(): IntersectionObserverEntry[] {
        return [];
    }
} as unknown as typeof IntersectionObserver;

URL.createObjectURL = vi.fn(() => 'blob:mock-url');
URL.revokeObjectURL = vi.fn();

Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    })),
});

// Suppress jsdom CSS parsing errors for custom properties
window.addEventListener('error', (event) => {
    const msg = event.error?.message || event.message || '';
    if (msg.includes('Cannot create property') && msg.includes('border')) {
        event.preventDefault();
        event.stopPropagation();
    }
});

// Mock SVG methods not available in jsdom
if (typeof SVGElement !== 'undefined') {
    Object.defineProperty(SVGElement.prototype, 'getBBox', {
        writable: true,
        value: vi.fn().mockReturnValue({
            x: 0,
            y: 0,
            width: 100,
            height: 100,
        }),
    });

    Object.defineProperty(SVGElement.prototype, 'getScreenCTM', {
        writable: true,
        value: vi.fn().mockReturnValue({
            a: 1,
            b: 0,
            c: 0,
            d: 1,
            e: 0,
            f: 0,
            inverse: vi.fn().mockReturnValue({ a: 1, b: 0, c: 0, d: 1, e: 0, f: 0 }),
        }),
    });

    Object.defineProperty(SVGElement.prototype, 'createSVGPoint', {
        writable: true,
        value: vi.fn().mockReturnValue({
            x: 0,
            y: 0,
            matrixTransform: vi.fn().mockReturnValue({ x: 0, y: 0 }),
        }),
    });
}
