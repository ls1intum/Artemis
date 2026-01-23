/**
 * Global Vitest Test Setup for Artemis
 *
 * Parallel to jest-test-setup.ts, this provides global mocks for Vitest tests.
 * NOTE: monaco-editor is mocked via path alias in vitest.config.ts.
 */
import '@angular/compiler';
import '@angular/localize/init';
import '@analogjs/vitest-angular/setup-zone';
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
import isoWeek from 'dayjs/esm/plugin/isoWeek';
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
dayjs.extend(isoWeek);
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

// Ensure Element.prototype.matches exists (used by PrimeNG/PrimeUIX utils)
if (typeof Element.prototype.matches === 'undefined') {
    Element.prototype.matches = function (selector: string): boolean {
        const matches = (this.ownerDocument || document).querySelectorAll(selector);
        let i = matches.length;
        while (--i >= 0 && matches.item(i) !== this) {}
        return i > -1;
    };
}

// Mock getComputedStyle to handle CSS custom properties
const originalGetComputedStyle = window.getComputedStyle;
window.getComputedStyle = function (element: Element, pseudoElt?: string | null): CSSStyleDeclaration {
    const style = originalGetComputedStyle.call(window, element, pseudoElt);
    return new Proxy(style, {
        get(target, prop) {
            const value = target[prop as keyof CSSStyleDeclaration];
            // Return empty string for CSS custom properties that may cause issues
            if (typeof prop === 'string' && prop.startsWith('--')) {
                return '';
            }
            return value;
        },
    });
} as typeof window.getComputedStyle;

// Suppress jsdom CSS parsing errors for custom properties
window.addEventListener('error', (event) => {
    const msg = event.error?.message || event.message || '';
    if (msg.includes('Cannot create property') && msg.includes('border')) {
        event.preventDefault();
        event.stopPropagation();
    }
});

// Suppress console.error for jsdom CSS parsing errors (PrimeNG custom properties) and navigation warnings
const originalConsoleError = console.error;
console.error = (...args: unknown[]) => {
    const msg = args[0];
    if (typeof msg === 'string') {
        // Suppress CSS variable parsing errors from jsdom
        if (msg.includes('Cannot create property') && msg.includes('border')) {
            return;
        }
        // Suppress jsdom "Not implemented" warnings for navigation
        if (msg.includes('Not implemented')) {
            return;
        }
    }
    originalConsoleError.apply(console, args);
};

// Also suppress via process.stderr for jsdom messages that bypass console
const originalStderrWrite = process.stderr.write.bind(process.stderr);
process.stderr.write = (chunk: string | Uint8Array, ...args: unknown[]): boolean => {
    const str = typeof chunk === 'string' ? chunk : chunk.toString();
    if (str.includes('Not implemented')) {
        return true;
    }
    return originalStderrWrite(chunk, ...(args as [BufferEncoding?, ((err?: Error) => void)?]));
};

// Patch CSSStyleDeclaration to handle CSS custom properties gracefully
const originalSetProperty = CSSStyleDeclaration.prototype.setProperty;
CSSStyleDeclaration.prototype.setProperty = function (property: string, value: string | null, priority?: string) {
    try {
        // If value contains CSS variables, skip setting it (jsdom can't handle them)
        if (value && typeof value === 'string' && value.includes('var(--')) {
            return;
        }
        return originalSetProperty.call(this, property, value, priority ?? '');
    } catch (error) {
        // Silently ignore CSS parsing errors from jsdom/cssstyle
        // Log unexpected errors that might indicate real issues
        if (error instanceof Error && !error.message.includes('Cannot create property')) {
            console.warn('Unexpected error in CSSStyleDeclaration.setProperty:', error);
        }
    }
};

// PrimeNG UIX motion relies on matchMedia; mock it globally to avoid setup in individual specs.
vi.mock('@primeuix/motion', () => ({
    __esModule: true,
    createMotion: vi.fn(() => ({
        enter: vi.fn(() => Promise.resolve()),
        leave: vi.fn(() => Promise.resolve()),
        cancel: vi.fn(),
        update: vi.fn(),
    })),
}));
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
