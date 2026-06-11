import { describe, expect, it, vi } from 'vitest';
import { BaseApiHttpService } from 'app/foundation/service/base-api-http.service';

describe('debounce', () => {
    vi.useFakeTimers();

    it('calls the callback after the specified delay', () => {
        const callback = vi.fn();
        const debouncedFunction = BaseApiHttpService.debounce(callback, 1000);

        debouncedFunction();
        expect(callback).not.toHaveBeenCalled();

        vi.advanceTimersByTime(1000);
        expect(callback).toHaveBeenCalledOnce();
    });

    it('resets the delay if called again within the delay period', () => {
        const callback = vi.fn();
        const debouncedFunction = BaseApiHttpService.debounce(callback, 1000);

        debouncedFunction();
        vi.advanceTimersByTime(500);
        debouncedFunction();
        vi.advanceTimersByTime(500);
        expect(callback).not.toHaveBeenCalled();

        vi.advanceTimersByTime(500);
        expect(callback).toHaveBeenCalledOnce();
    });

    it('calls the callback with the correct arguments', () => {
        const callback = vi.fn();
        const debouncedFunction = BaseApiHttpService.debounce(callback, 1000);

        debouncedFunction('arg1', 'arg2');
        vi.advanceTimersByTime(1000);
        expect(callback).toHaveBeenCalledWith('arg1', 'arg2');
    });

    it('handles multiple calls correctly', () => {
        const callback = vi.fn();
        const debouncedFunction = BaseApiHttpService.debounce(callback, 1000);

        debouncedFunction();
        vi.advanceTimersByTime(1000);
        expect(callback).toHaveBeenCalledOnce();

        debouncedFunction();
        vi.advanceTimersByTime(1000);
        expect(callback).toHaveBeenCalledTimes(2);
    });
});
