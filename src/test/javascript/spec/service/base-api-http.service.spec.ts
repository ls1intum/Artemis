import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';

describe('debounce', () => {
    jest.useFakeTimers();

    it('calls the callback after the specified delay', () => {
        const callback = jest.fn();
        const debouncedFunction = BaseApiHttpService.debounce(callback, 1000);

        debouncedFunction();
        expect(callback).not.toHaveBeenCalled();

        jest.advanceTimersByTime(1000);
        expect(callback).toHaveBeenCalledOnce();
    });

    it('resets the delay if called again within the delay period', () => {
        const callback = jest.fn();
        const debouncedFunction = BaseApiHttpService.debounce(callback, 1000);

        debouncedFunction();
        jest.advanceTimersByTime(500);
        debouncedFunction();
        jest.advanceTimersByTime(500);
        expect(callback).not.toHaveBeenCalled();

        jest.advanceTimersByTime(500);
        expect(callback).toHaveBeenCalledOnce();
    });

    it('calls the callback with the correct arguments', () => {
        const callback = jest.fn();
        const debouncedFunction = BaseApiHttpService.debounce(callback, 1000);

        debouncedFunction('arg1', 'arg2');
        jest.advanceTimersByTime(1000);
        expect(callback).toHaveBeenCalledWith('arg1', 'arg2');
    });

    it('handles multiple calls correctly', () => {
        const callback = jest.fn();
        const debouncedFunction = BaseApiHttpService.debounce(callback, 1000);

        debouncedFunction();
        jest.advanceTimersByTime(1000);
        expect(callback).toHaveBeenCalledOnce();

        debouncedFunction();
        jest.advanceTimersByTime(1000);
        expect(callback).toHaveBeenCalledTimes(2);
    });
});
