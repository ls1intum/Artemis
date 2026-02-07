import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { IrisOnboardingService } from './iris-onboarding.service';

describe('IrisOnboardingService', () => {
    setupTestBed({ zoneless: true });

    let service: IrisOnboardingService;
    let modalService: NgbModal;

    const STORAGE_KEY = 'iris-onboarding-completed';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [IrisOnboardingService, { provide: NgbModal, useValue: { open: vi.fn() } }],
        });

        service = TestBed.inject(IrisOnboardingService);
        modalService = TestBed.inject(NgbModal);
        localStorage.removeItem(STORAGE_KEY);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        localStorage.removeItem(STORAGE_KEY);
    });

    describe('hasCompletedOnboarding', () => {
        it('should return false when onboarding has not been completed', () => {
            expect(service.hasCompletedOnboarding()).toBeFalsy();
        });

        it('should return true when onboarding has been completed', () => {
            localStorage.setItem(STORAGE_KEY, 'true');
            expect(service.hasCompletedOnboarding()).toBeTruthy();
        });
    });

    describe('markOnboardingCompleted', () => {
        it('should set localStorage key to true', () => {
            service.markOnboardingCompleted();
            expect(localStorage.getItem(STORAGE_KEY)).toBe('true');
        });
    });

    describe('resetOnboarding', () => {
        it('should remove localStorage key', () => {
            localStorage.setItem(STORAGE_KEY, 'true');
            service.resetOnboarding();
            expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
        });
    });

    describe('openOnboardingModal', () => {
        it('should open the modal with correct options', async () => {
            const mockModalRef = { result: Promise.resolve('finish') } as NgbModalRef;
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

            const result = await service.openOnboardingModal();

            expect(modalService.open).toHaveBeenCalledOnce();
            const openCall = vi.mocked(modalService.open).mock.calls[0];
            expect(openCall[1]).toEqual(
                expect.objectContaining({
                    backdrop: false,
                    keyboard: false,
                    windowClass: 'iris-onboarding-modal-window',
                    modalDialogClass: 'iris-onboarding-dialog',
                }),
            );
            expect(result).toBe('finish');
        });

        it('should return undefined when modal is dismissed', async () => {
            const mockModalRef = { result: Promise.reject('dismissed') } as NgbModalRef;
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

            const result = await service.openOnboardingModal();

            expect(result).toBeUndefined();
        });

        it('should not open a second modal if one is already open', async () => {
            const neverResolve = new Promise<string>(() => {});
            const mockModalRef = { result: neverResolve } as NgbModalRef;
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

            // Start first modal (won't resolve)
            const firstCall = service.openOnboardingModal();

            // Try opening second modal immediately
            const secondResult = await service.openOnboardingModal();

            expect(modalService.open).toHaveBeenCalledOnce();
            expect(secondResult).toBeUndefined();

            // Clean up by not awaiting the never-resolving promise
            void firstCall;
        });

        it('should clear modalRef after modal closes', async () => {
            const mockModalRef = { result: Promise.resolve('finish') } as NgbModalRef;
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

            await service.openOnboardingModal();

            // Should be able to open again
            const mockModalRef2 = { result: Promise.resolve('finish') } as NgbModalRef;
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef2);
            await service.openOnboardingModal();

            expect(modalService.open).toHaveBeenCalledTimes(2);
        });
    });

    describe('showOnboardingIfNeeded', () => {
        it('should delegate to openOnboardingModal', async () => {
            const mockModalRef = { result: Promise.resolve('finish') } as NgbModalRef;
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

            const result = await service.showOnboardingIfNeeded();

            expect(modalService.open).toHaveBeenCalledOnce();
            expect(result).toBe('finish');
        });
    });
});
