/**
 * Vitest tests for BrowserFingerprintService.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

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
    setupTestBed({ zoneless: true });

    let service: BrowserFingerprintService;
    let localStorageService: LocalStorageService;

    const BROWSER_INSTANCE_KEY = 'instanceIdentifier';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [BrowserFingerprintService, LocalStorageService],
        });

        service = TestBed.inject(BrowserFingerprintService);
        localStorageService = TestBed.inject(LocalStorageService);
    });

    describe('initialize', () => {
        it('should set fingerprint and instance when browserFingerprintsEnabled is true', async () => {
            const storeSpy = vi.spyOn(localStorageService, 'store');
            vi.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);

            service.initialize(true);

            // Wait for async operations
            await vi.waitFor(() => {
                expect(service.browserFingerprint.value).toBe('test-visitor-id');
            });
            expect(service.browserInstanceId.value).toBeDefined();
            expect(storeSpy).toHaveBeenCalledWith(BROWSER_INSTANCE_KEY, expect.any(String));
        });

        it('should set fingerprint and instance when browserFingerprintsEnabled is undefined', async () => {
            const storeSpy = vi.spyOn(localStorageService, 'store');
            vi.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);

            service.initialize(undefined);

            await vi.waitFor(() => {
                expect(service.browserFingerprint.value).toBe('test-visitor-id');
            });
            expect(service.browserInstanceId.value).toBeDefined();
            expect(storeSpy).toHaveBeenCalled();
        });

        it('should clear instance when browserFingerprintsEnabled is false', () => {
            const removeSpy = vi.spyOn(localStorageService, 'remove');

            service.initialize(false);

            expect(removeSpy).toHaveBeenCalledWith(BROWSER_INSTANCE_KEY);
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
            const storeSpy = vi.spyOn(localStorageService, 'store');

            service.initialize(true);

            expect(service.browserInstanceId.value).toBeDefined();
            expect(storeSpy).toHaveBeenCalledWith(BROWSER_INSTANCE_KEY, expect.any(String));
        });
    });
});
