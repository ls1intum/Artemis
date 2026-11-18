/**
 * Mock class for the global ResizeObserver class.
 * https://developer.mozilla.org/en-US/docs/Web/API/ResizeObserver
 */
export class MockResizeObserver {
    constructor(callback: ResizeObserverCallback) {
        // Do nothing
    }

    observe(element: Element): void {
        // Do nothing
    }

    unobserve(element: Element): void {
        // Do nothing
    }

    disconnect(): void {
        // Do nothing
    }
}
