import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AccountService } from 'app/core/auth/account.service';
import { IrisOnboardingService, OnboardingResult } from './iris-onboarding.service';

describe('IrisOnboardingService', () => {
    setupTestBed({ zoneless: true });

    let service: IrisOnboardingService;
    let dialogService: DialogService;
    let matchMediaMock: ReturnType<typeof vi.spyOn>;

    const MOCK_USER_ID = 42;
    const STORAGE_KEY = `iris-onboarding-completed-${MOCK_USER_ID}`;

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

    function createMockDialogRef(closeSubject: Subject<OnboardingResult | undefined>): DynamicDialogRef {
        return { onClose: closeSubject.asObservable() } as unknown as DynamicDialogRef;
    }

    beforeEach(() => {
        matchMediaMock = vi.spyOn(window, 'matchMedia');
        setDesktopViewport(true);

        TestBed.configureTestingModule({
            providers: [
                IrisOnboardingService,
                { provide: DialogService, useValue: { open: vi.fn() } },
                { provide: AccountService, useValue: { userIdentity: () => ({ id: MOCK_USER_ID }) } },
            ],
        });

        service = TestBed.inject(IrisOnboardingService);
        dialogService = TestBed.inject(DialogService);
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

            expect(dialogService.open).not.toHaveBeenCalled();
            expect(result).toBeUndefined();
        });

        it('should open the modal with correct options', async () => {
            const closeSubject = new Subject<OnboardingResult | undefined>();
            vi.spyOn(dialogService, 'open').mockReturnValue(createMockDialogRef(closeSubject));

            const resultPromise = service.openOnboardingModal();
            closeSubject.next({ action: 'finish' });
            closeSubject.complete();
            const result = await resultPromise;

            expect(dialogService.open).toHaveBeenCalledOnce();
            const openCall = vi.mocked(dialogService.open).mock.calls[0];
            expect(openCall[1]).toEqual(
                expect.objectContaining({
                    modal: false,
                    closable: false,
                    showHeader: false,
                    styleClass: 'iris-onboarding-dialog',
                }),
            );
            expect(result).toEqual({ action: 'finish' });
        });

        it('should mark onboarding as completed after modal closes', async () => {
            const closeSubject = new Subject<OnboardingResult | undefined>();
            vi.spyOn(dialogService, 'open').mockReturnValue(createMockDialogRef(closeSubject));

            const resultPromise = service.openOnboardingModal();
            closeSubject.next({ action: 'finish' });
            closeSubject.complete();
            await resultPromise;

            expect(service.hasCompletedOnboarding()).toBeTruthy();
        });

        it('should return undefined when modal is dismissed', async () => {
            const closeSubject = new Subject<OnboardingResult | undefined>();
            vi.spyOn(dialogService, 'open').mockReturnValue(createMockDialogRef(closeSubject));

            const resultPromise = service.openOnboardingModal();
            closeSubject.next(undefined);
            closeSubject.complete();
            const result = await resultPromise;

            expect(result).toBeUndefined();
            expect(service.hasCompletedOnboarding()).toBeTruthy();
        });

        it('should not open a second modal if one is already open', async () => {
            const closeSubject = new Subject<OnboardingResult | undefined>();
            vi.spyOn(dialogService, 'open').mockReturnValue(createMockDialogRef(closeSubject));

            const firstCall = service.openOnboardingModal();
            const secondCall = service.openOnboardingModal();

            expect(dialogService.open).toHaveBeenCalledOnce();

            closeSubject.next({ action: 'finish' });
            closeSubject.complete();

            const firstResult = await firstCall;
            const secondResult = await secondCall;
            expect(firstResult).toEqual({ action: 'finish' });
            expect(secondResult).toEqual({ action: 'finish' });
        });

        it('should clear dialogRef after modal closes', async () => {
            const closeSubject1 = new Subject<OnboardingResult | undefined>();
            const closeSubject2 = new Subject<OnboardingResult | undefined>();
            vi.spyOn(dialogService, 'open').mockReturnValueOnce(createMockDialogRef(closeSubject1)).mockReturnValueOnce(createMockDialogRef(closeSubject2));

            const firstPromise = service.openOnboardingModal();
            closeSubject1.next({ action: 'finish' });
            closeSubject1.complete();
            await firstPromise;

            const secondPromise = service.openOnboardingModal();
            closeSubject2.next({ action: 'finish' });
            closeSubject2.complete();
            await secondPromise;

            expect(dialogService.open).toHaveBeenCalledTimes(2);
        });
    });

    describe('showOnboardingIfNeeded', () => {
        it('should return undefined and not open modal on non-desktop viewport', async () => {
            setDesktopViewport(false);
            const result = await service.showOnboardingIfNeeded(true);

            expect(dialogService.open).not.toHaveBeenCalled();
            expect(result).toBeUndefined();
        });

        it('should delegate to openOnboardingModal', async () => {
            const closeSubject = new Subject<OnboardingResult | undefined>();
            vi.spyOn(dialogService, 'open').mockReturnValue(createMockDialogRef(closeSubject));

            const resultPromise = service.showOnboardingIfNeeded(true);
            closeSubject.next({ action: 'finish' });
            closeSubject.complete();
            const result = await resultPromise;

            expect(dialogService.open).toHaveBeenCalledOnce();
            expect(result).toEqual({ action: 'finish' });
        });

        it('should return undefined and skip opening when onboarding has been completed', async () => {
            service.markOnboardingCompleted();

            const result = await service.showOnboardingIfNeeded(true);

            expect(result).toBeUndefined();
            expect(dialogService.open).not.toHaveBeenCalled();
        });

        it('should return undefined and skip opening when not in empty state', async () => {
            const result = await service.showOnboardingIfNeeded(false);

            expect(result).toBeUndefined();
            expect(dialogService.open).not.toHaveBeenCalled();
        });
    });
});
