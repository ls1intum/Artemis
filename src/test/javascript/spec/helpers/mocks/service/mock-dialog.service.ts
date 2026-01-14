import { Subject } from 'rxjs';

/**
 * Creates a mock function compatible with both Jest and Vitest.
 */
function createMockFn<T = unknown>(): jest.Mock<T> | (() => T) {
    // Check if we're in Jest environment
    if (typeof jest !== 'undefined' && jest.fn) {
        return jest.fn();
    }
    // For Vitest or other environments, return a simple function
    return (() => {}) as unknown as jest.Mock<T>;
}

/**
 * Mock for PrimeNG DialogService used in tests.
 * This is needed because DeleteDialogService depends on DialogService.
 */
export class MockDialogService {
    private mockDialogRef = {
        onClose: new Subject<void>(),
        close: createMockFn(),
    };

    open = (() => this.mockDialogRef) as unknown as jest.Mock;
}
