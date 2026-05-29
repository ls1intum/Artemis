import { BaseApiHttpService } from 'app/foundation/service/base-api-http.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('debounce', () => {
    setupTestBed({ zoneless: true });

    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('calls the callback after the specified delay', async () => {
        const callback = vi.fn();
        const debouncedFunction = BaseApiHttpService.debounce(callback, 1000);

        debouncedFunction();
        expect(callback).not.toHaveBeenCalled();

        await vi.advanceTimersByTimeAsync(1000);
        expect(callback).toHaveBeenCalledOnce();
    });

    it('resets the delay if called again within the delay period', async () => {
        const callback = vi.fn();
        const debouncedFunction = BaseApiHttpService.debounce(callback, 1000);

        debouncedFunction();
        await vi.advanceTimersByTimeAsync(500);
        debouncedFunction();
        await vi.advanceTimersByTimeAsync(500);
        expect(callback).not.toHaveBeenCalled();

        await vi.advanceTimersByTimeAsync(500);
        expect(callback).toHaveBeenCalledOnce();
    });

    it('calls the callback with the correct arguments', async () => {
        const callback = vi.fn();
        const debouncedFunction = BaseApiHttpService.debounce(callback, 1000);

        debouncedFunction('arg1', 'arg2');
        await vi.advanceTimersByTimeAsync(1000);
        expect(callback).toHaveBeenCalledWith('arg1', 'arg2');
    });

    it('handles multiple calls correctly', async () => {
        const callback = vi.fn();
        const debouncedFunction = BaseApiHttpService.debounce(callback, 1000);

        debouncedFunction();
        await vi.advanceTimersByTimeAsync(1000);
        expect(callback).toHaveBeenCalledOnce();

        debouncedFunction();
        await vi.advanceTimersByTimeAsync(1000);
        expect(callback).toHaveBeenCalledTimes(2);
    });
});
