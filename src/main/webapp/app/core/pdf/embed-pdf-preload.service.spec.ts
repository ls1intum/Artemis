import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { PdfEngineService } from 'app/core/pdf/pdf-engine.service';
import { EmbedPdfPreloadService } from 'app/core/pdf/embed-pdf-preload.service';

describe('EmbedPdfPreloadService', () => {
    setupTestBed({ zoneless: true });

    let service: EmbedPdfPreloadService;
    let getEngine: ReturnType<typeof vi.fn>;
    let isAuthenticated: ReturnType<typeof vi.fn>;
    let originalIdleCallback: typeof window.requestIdleCallback | undefined;

    beforeEach(() => {
        getEngine = vi.fn(() => Promise.resolve({}));
        isAuthenticated = vi.fn(() => true);
        originalIdleCallback = window.requestIdleCallback;

        TestBed.configureTestingModule({
            providers: [EmbedPdfPreloadService, { provide: PdfEngineService, useValue: { getEngine } }, { provide: AccountService, useValue: { isAuthenticated } }],
        });
        service = TestBed.inject(EmbedPdfPreloadService);
    });

    afterEach(() => {
        // Restore the (possibly stubbed) idle-callback hook so tests stay isolated.
        if (originalIdleCallback) {
            window.requestIdleCallback = originalIdleCallback;
        } else {
            delete (window as { requestIdleCallback?: unknown }).requestIdleCallback;
        }
        vi.restoreAllMocks();
    });

    it('should warm the engine via requestIdleCallback when the user is authenticated', () => {
        const idle = vi.fn((cb: () => void) => cb());
        window.requestIdleCallback = idle as unknown as typeof window.requestIdleCallback;

        service.schedulePreload();

        expect(idle).toHaveBeenCalledOnce();
        expect(getEngine).toHaveBeenCalledOnce();
    });

    it('should not warm the engine when the user is not authenticated', () => {
        isAuthenticated.mockReturnValue(false);
        const idle = vi.fn((cb: () => void) => cb());
        window.requestIdleCallback = idle as unknown as typeof window.requestIdleCallback;

        service.schedulePreload();

        expect(idle).toHaveBeenCalledOnce();
        expect(getEngine).not.toHaveBeenCalled();
    });

    it('should only schedule once even when called multiple times', () => {
        const idle = vi.fn();
        window.requestIdleCallback = idle as unknown as typeof window.requestIdleCallback;

        service.schedulePreload();
        service.schedulePreload();
        service.schedulePreload();

        expect(idle).toHaveBeenCalledOnce();
    });

    it('should fall back to setTimeout when requestIdleCallback is unavailable', () => {
        delete (window as { requestIdleCallback?: unknown }).requestIdleCallback;
        const setTimeoutSpy = vi.spyOn(window, 'setTimeout').mockImplementation(((cb: () => void) => {
            cb();
            return 0;
        }) as unknown as typeof window.setTimeout);

        service.schedulePreload();

        expect(setTimeoutSpy).toHaveBeenCalledOnce();
        expect(getEngine).toHaveBeenCalledOnce();
    });

    it('should swallow engine warm-up failures without throwing', async () => {
        getEngine.mockRejectedValue(new Error('warm-up failed'));
        const idle = vi.fn((cb: () => void) => cb());
        window.requestIdleCallback = idle as unknown as typeof window.requestIdleCallback;

        expect(() => service.schedulePreload()).not.toThrow();
        expect(getEngine).toHaveBeenCalledOnce();
        // Let the swallowed rejection settle so it does not surface as an unhandled rejection.
        await Promise.resolve();
    });
});
