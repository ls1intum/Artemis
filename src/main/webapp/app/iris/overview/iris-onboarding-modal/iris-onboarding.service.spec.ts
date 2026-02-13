import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { IrisOnboardingService } from './iris-onboarding.service';

describe('IrisOnboardingService', () => {
    setupTestBed({ zoneless: true });

    let service: IrisOnboardingService;
    let modalService: NgbModal;
    let matchMediaMock: ReturnType<typeof vi.spyOn>;

    const STORAGE_KEY = 'iris-onboarding-completed';

    const setDesktopViewport = (isDesktop: boolean) => {
        matchMediaMock.mockImplementation((query: string) => {
            const matches = query === '(min-width: 992px)' ? isDesktop : false;
            return {
                matches,
                media: query,
                onchange: null,
                addListener: vi.fn(),
                removeListener: vi.fn(),
                addEventListener: vi.fn(),
                removeEventListener: vi.fn(),
                dispatchEvent: vi.fn(),
            } as unknown as MediaQueryList;
        });
    };

    beforeEach(() => {
        matchMediaMock = vi.spyOn(window, 'matchMedia');
        setDesktopViewport(true);

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
        it('should not open the modal on non-desktop viewport', async () => {
            setDesktopViewport(false);
            const result = await service.openOnboardingModal();

            expect(modalService.open).not.toHaveBeenCalled();
            expect(result).toBeUndefined();
        });

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
            expect(result).toEqual({ action: 'finish' });
        });

        it('should return promptSelected result when modal closes with prompt selection', async () => {
            const promptResult = { action: 'promptSelected', promptKey: 'artemisApp.iris.onboarding.step4.prompts.explainConceptStarter' };
            const mockModalRef = { result: Promise.resolve(promptResult) } as NgbModalRef;
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

            const result = await service.openOnboardingModal();

            expect(result).toEqual(promptResult);
        });

        it('should mark onboarding as completed after modal closes', async () => {
            const mockModalRef = { result: Promise.resolve('finish') } as NgbModalRef;
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

            await service.openOnboardingModal();

            expect(service.hasCompletedOnboarding()).toBeTruthy();
        });

        it('should pass hasAvailableExercises to the modal component', async () => {
            const setHasAvailableExercises = vi.fn();
            const mockModalRef = {
                result: Promise.resolve('finish'),
                componentInstance: { hasAvailableExercises: { set: setHasAvailableExercises } },
            } as NgbModalRef;
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

            await service.openOnboardingModal(false);

            expect(setHasAvailableExercises).toHaveBeenCalledWith(false);
        });

        it('should return undefined when modal is dismissed', async () => {
            const mockModalRef = { result: Promise.reject('dismissed') } as NgbModalRef;
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

            const result = await service.openOnboardingModal();

            expect(result).toBeUndefined();
            expect(service.hasCompletedOnboarding()).toBeTruthy();
        });

        it('should not open a second modal if one is already open', async () => {
            let resolveModal: (value: unknown) => void;
            const modalPromise = new Promise((resolve) => {
                resolveModal = resolve;
            });
            const mockModalRef = { result: modalPromise } as NgbModalRef;
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

            // Start first modal (won't resolve yet)
            const firstCall = service.openOnboardingModal();

            // Try opening second modal — should NOT open a new modal
            const secondCall = service.openOnboardingModal();

            expect(modalService.open).toHaveBeenCalledOnce();

            // Resolve the modal with a prompt selection
            const promptResult = { action: 'promptSelected', promptKey: 'artemisApp.iris.onboarding.step4.prompts.explainConceptStarter' };
            resolveModal!(promptResult);

            // Both calls should receive the same result
            const firstResult = await firstCall;
            const secondResult = await secondCall;
            expect(firstResult).toEqual(promptResult);
            expect(secondResult).toEqual(promptResult);
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
        it('should return undefined and not open modal on non-desktop viewport', async () => {
            setDesktopViewport(false);
            const result = await service.showOnboardingIfNeeded();

            expect(modalService.open).not.toHaveBeenCalled();
            expect(result).toBeUndefined();
        });

        it('should delegate to openOnboardingModal', async () => {
            const mockModalRef = { result: Promise.resolve('finish') } as NgbModalRef;
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

            const result = await service.showOnboardingIfNeeded();

            expect(modalService.open).toHaveBeenCalledOnce();
            expect(result).toEqual({ action: 'finish' });
        });

        it('should return undefined and skip opening when onboarding has been completed', async () => {
            service.markOnboardingCompleted();

            const result = await service.showOnboardingIfNeeded();

            expect(result).toBeUndefined();
            expect(modalService.open).not.toHaveBeenCalled();
        });
    });
});
