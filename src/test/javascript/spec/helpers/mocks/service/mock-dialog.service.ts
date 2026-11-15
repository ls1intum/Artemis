import { Subject } from 'rxjs';
import { type Mock, vi } from 'vitest';

/**
 * Supplies the observable close event and close method consumed by dialog callers in tests.
 */
export class MockDialogService {
    private mockDialogRef = {
        onClose: new Subject<void>(),
        close: vi.fn(),
    };

    open = vi.fn(() => this.mockDialogRef) as unknown as Mock;
}
