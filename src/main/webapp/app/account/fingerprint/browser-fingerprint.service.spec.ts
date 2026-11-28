/**
 * Vitest tests for BrowserFingerprintService.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { BrowserFingerprintService } from 'app/account/fingerprint/browser-fingerprint.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import FingerprintJS from '@fingerprintjs/fingerprintjs';

// Mock FingerprintJS
vi.mock('@fingerprintjs/fingerprintjs', () => ({
    default: {
        load: vi.fn(() =>
            Promise.resolve({
                get: vi.fn(() => Promise.resolve({ visitorId: 'test-visitor-id' })),
            }),
        ),
    },
}));

describe('BrowserFingerprintService', () => {
    let service: BrowserFingerprintService;
    let localStorageService: LocalStorageService;
    let sessionStorageService: SessionStorageService;

    const BROWSER_INSTANCE_KEY = 'instanceIdentifier';
    const BROWSER_SESSION_KEY = 'sessionIdentifier';

    beforeEach(() => {
        vi.mocked(FingerprintJS.load).mockClear();
        TestBed.configureTestingModule({
            providers: [BrowserFingerprintService, LocalStorageService, SessionStorageService],
        });

        service = TestBed.inject(BrowserFingerprintService);
        localStorageService = TestBed.inject(LocalStorageService);
        sessionStorageService = TestBed.inject(SessionStorageService);
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.unstubAllGlobals();
        delete (window as { requestIdleCallback?: unknown }).requestIdleCallback;
    });

    describe('initialize', () => {
        it('should set instance and session identifiers when browserFingerprintsEnabled is true, without loading the fingerprint library', () => {
            const localStoreSpy = vi.spyOn(localStorageService, 'store');
            const sessionStoreSpy = vi.spyOn(sessionStorageService, 'store');
            vi.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);
            vi.spyOn(sessionStorageService, 'retrieve').mockReturnValue(undefined);

            service.initialize(true);

            expect(service.browserInstanceId.value).toBeDefined();
            expect(service.browserSessionId.value).toBeDefined();
            expect(localStoreSpy).toHaveBeenCalledWith(BROWSER_INSTANCE_KEY, expect.any(String));
            expect(sessionStoreSpy).toHaveBeenCalledWith(BROWSER_SESSION_KEY, expect.any(String));
            expect(service.browserFingerprint.value).toBeUndefined();
            expect(FingerprintJS.load).not.toHaveBeenCalled();
        });

        it('should set instance and session identifiers when browserFingerprintsEnabled is undefined', () => {
            const localStoreSpy = vi.spyOn(localStorageService, 'store');
            const sessionStoreSpy = vi.spyOn(sessionStorageService, 'store');
            vi.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);
            vi.spyOn(sessionStorageService, 'retrieve').mockReturnValue(undefined);

            service.initialize(undefined);

            expect(service.browserInstanceId.value).toBeDefined();
            expect(service.browserSessionId.value).toBeDefined();
            expect(localStoreSpy).toHaveBeenCalled();
            expect(sessionStoreSpy).toHaveBeenCalled();
        });

        it('should clear instance but still initialize session when browserFingerprintsEnabled is false', () => {
            const removeSpy = vi.spyOn(localStorageService, 'remove');
            vi.spyOn(sessionStorageService, 'retrieve').mockReturnValue(undefined);
            const sessionStoreSpy = vi.spyOn(sessionStorageService, 'store');

            service.initialize(false);

            expect(removeSpy).toHaveBeenCalledWith(BROWSER_INSTANCE_KEY);
            // Session identifier must always be initialized for message routing
            expect(service.browserSessionId.value).toBeDefined();
            expect(sessionStoreSpy).toHaveBeenCalledWith(BROWSER_SESSION_KEY, expect.any(String));
        });

        it('should still initialize without throwing when crypto.randomUUID is unavailable (insecure-context bootstrap)', () => {
            // Reproduces the fatal E2E bootstrap error: over a plain-HTTP origin window.crypto.randomUUID is undefined.
            const realCrypto = window.crypto;
            vi.stubGlobal('crypto', { getRandomValues: (array: Uint8Array<ArrayBuffer>) => realCrypto.getRandomValues(array) });
            vi.spyOn(sessionStorageService, 'retrieve').mockReturnValue(undefined);
            try {
                expect(() => service.initialize(false)).not.toThrow();
                expect(service.browserSessionId.value).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
            } finally {
                vi.stubGlobal('crypto', realCrypto);
            }
        });

        it('should use existing instance identifier from localStorage', () => {
            const existingId = 'existing-instance-id';
            vi.spyOn(localStorageService, 'retrieve').mockReturnValue(existingId);
            const storeSpy = vi.spyOn(localStorageService, 'store');

            service.initialize(true);

            expect(service.browserInstanceId.value).toBe(existingId);
            expect(storeSpy).not.toHaveBeenCalled();
        });

        it('should generate new instance identifier when not in localStorage', () => {
            vi.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);
            vi.spyOn(sessionStorageService, 'retrieve').mockReturnValue(undefined);
            const storeSpy = vi.spyOn(localStorageService, 'store');

            service.initialize(true);

            expect(service.browserInstanceId.value).toBeDefined();
            expect(storeSpy).toHaveBeenCalledWith(BROWSER_INSTANCE_KEY, expect.any(String));
        });

        it('should use existing session identifier from sessionStorage', () => {
            const existingSessionId = 'existing-session-id';
            vi.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);
            vi.spyOn(sessionStorageService, 'retrieve').mockReturnValue(existingSessionId);
            const sessionStoreSpy = vi.spyOn(sessionStorageService, 'store');

            service.initialize(true);

            expect(service.browserSessionId.value).toBe(existingSessionId);
            expect(sessionStoreSpy).not.toHaveBeenCalled();
        });

        it('should generate new session identifier when not in sessionStorage', () => {
            vi.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);
            vi.spyOn(sessionStorageService, 'retrieve').mockReturnValue(undefined);
            const sessionStoreSpy = vi.spyOn(sessionStorageService, 'store');

            service.initialize(true);

            expect(service.browserSessionId.value).toBeDefined();
            expect(sessionStoreSpy).toHaveBeenCalledWith(BROWSER_SESSION_KEY, expect.any(String));
        });
    });

    describe('startWhenIdle', () => {
        it('should generate the fingerprint when the browser is idle', async () => {
            const requestIdleCallback = vi.fn((callback: () => void) => callback());
            (window as { requestIdleCallback?: unknown }).requestIdleCallback = requestIdleCallback;
            service.initialize(true);
            expect(FingerprintJS.load).not.toHaveBeenCalled();

            service.startWhenIdle();

            expect(requestIdleCallback).toHaveBeenCalledWith(expect.any(Function), { timeout: 5000 });
            await vi.waitFor(() => expect(service.browserFingerprint.value).toBe('test-visitor-id'));
        });

        it('should fall back to a timer when the browser has no idle callback', async () => {
            vi.useFakeTimers();
            service.initialize(true);

            service.startWhenIdle();
            await vi.advanceTimersByTimeAsync(2999);
            expect(FingerprintJS.load).not.toHaveBeenCalled();
            await vi.advanceTimersByTimeAsync(1);

            expect(FingerprintJS.load).toHaveBeenCalledOnce();
        });

        it('should schedule the generation only once however often it is called', () => {
            const requestIdleCallback = vi.fn();
            (window as { requestIdleCallback?: unknown }).requestIdleCallback = requestIdleCallback;
            service.initialize(true);

            service.startWhenIdle();
            service.startWhenIdle();

            expect(requestIdleCallback).toHaveBeenCalledOnce();
        });

        it('should do nothing before the service is initialized or when fingerprints are disabled', () => {
            const requestIdleCallback = vi.fn();
            (window as { requestIdleCallback?: unknown }).requestIdleCallback = requestIdleCallback;

            service.startWhenIdle();
            service.initialize(false);
            service.startWhenIdle();

            expect(requestIdleCallback).not.toHaveBeenCalled();
            expect(FingerprintJS.load).not.toHaveBeenCalled();
        });
    });

    describe('isFingerprintPending', () => {
        it('should be false before initialization and when fingerprints are disabled', () => {
            expect(service.isFingerprintPending()).toBe(false);

            service.initialize(false);

            expect(service.isFingerprintPending()).toBe(false);
        });

        it('should be true while the fingerprint is enabled but not available, and false once it is', async () => {
            service.initialize(true);
            expect(service.isFingerprintPending()).toBe(true);

            await service.ensureFingerprint();

            expect(service.isFingerprintPending()).toBe(false);
        });
    });

    describe('ensureFingerprint', () => {
        it('should start the generation at once and resolve to the fingerprint', async () => {
            service.initialize(true);

            await expect(service.ensureFingerprint()).resolves.toBe('test-visitor-id');

            expect(service.browserFingerprint.value).toBe('test-visitor-id');
        });

        it('should load the library only once for repeated calls', async () => {
            service.initialize(true);

            await service.ensureFingerprint();
            await service.ensureFingerprint();

            expect(FingerprintJS.load).toHaveBeenCalledOnce();
        });

        it('should resolve to undefined without loading the library when fingerprints are disabled or the service is not initialized', async () => {
            await expect(service.ensureFingerprint()).resolves.toBeUndefined();
            service.initialize(false);
            await expect(service.ensureFingerprint()).resolves.toBeUndefined();

            expect(FingerprintJS.load).not.toHaveBeenCalled();
        });

        it('should stop waiting after the time limit when the library is slow', async () => {
            vi.useFakeTimers();
            vi.mocked(FingerprintJS.load).mockReturnValueOnce(new Promise(() => {}));
            service.initialize(true);

            const result = service.ensureFingerprint();
            await vi.advanceTimersByTimeAsync(3000);

            await expect(result).resolves.toBeUndefined();
            expect(service.browserFingerprint.value).toBeUndefined();
        });
    });

    describe('when the library fails to load', () => {
        it('should leave the fingerprint unset and not reject, while the identifiers are still set', async () => {
            vi.mocked(FingerprintJS.load).mockRejectedValueOnce(new Error('Failed to fetch dynamically imported module'));
            const unhandledRejection = vi.fn();
            process.on('unhandledRejection', unhandledRejection);

            service.initialize(true);
            const result = await service.ensureFingerprint();
            await new Promise((resolve) => setTimeout(resolve, 0));

            process.off('unhandledRejection', unhandledRejection);
            expect(result).toBeUndefined();
            expect(unhandledRejection).not.toHaveBeenCalled();
            expect(service.browserFingerprint.value).toBeUndefined();
            expect(service.browserInstanceId.value).toBeDefined();
            expect(service.browserSessionId.value).toBeDefined();
        });
    });
});
