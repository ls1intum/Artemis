import { Subject } from 'rxjs';
import { type Mock, vi } from 'vitest';

/**
 * Mock for PrimeNG DialogService used in tests.
 * This is needed because DeleteDialogService depends on DialogService.
 */
export class MockDialogService {
    private mockDialogRef = {
        onClose: new Subject<void>(),
        close: vi.fn(),
    };

    open = vi.fn(() => this.mockDialogRef) as unknown as Mock;
}
