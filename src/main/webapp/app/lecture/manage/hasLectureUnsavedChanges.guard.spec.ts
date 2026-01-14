import { ActivatedRouteSnapshot, GuardResult, MaybeAsync, Router, RouterStateSnapshot } from '@angular/router';
import { LectureUnsavedChangesComponent, hasLectureUnsavedChangesGuard } from 'app/lecture/manage/hasLectureUnsavedChanges.guard';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Observable, Subject, firstValueFrom, of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('hasLectureUnsavedChanges', () => {
    setupTestBed({ zoneless: true });

    let component: LectureUnsavedChangesComponent;
    let currentRoute: ActivatedRouteSnapshot;
    let currentState: RouterStateSnapshot;
    let nextState: RouterStateSnapshot;
    let mockDialogRef: DynamicDialogRef;
    let dialogCloseSubject: Subject<boolean | undefined>;

    beforeEach(() => {
        dialogCloseSubject = new Subject<boolean | undefined>();
        mockDialogRef = {
            onClose: dialogCloseSubject.asObservable(),
            close: vi.fn(),
        } as unknown as DynamicDialogRef;

        TestBed.configureTestingModule({
            providers: [
                { provide: Router, useClass: MockRouter },
                {
                    provide: DialogService,
                    useValue: {
                        open: vi.fn().mockReturnValue(mockDialogRef),
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        component = {
            shouldDisplayDismissWarning: true,
            isChangeMadeToTitleSection: vi.fn().mockReturnValue(true),
            isChangeMadeToPeriodSection: vi.fn().mockReturnValue(true),
            isChangeMadeToTitleOrPeriodSection: true,
        };

        currentRoute = {} as ActivatedRouteSnapshot;
        currentState = {} as RouterStateSnapshot;
        nextState = {} as RouterStateSnapshot;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should return true if warning is not bypassed by shouldDisplayDismissWarning variable but no changes were made', async () => {
        component.shouldDisplayDismissWarning = true;
        component.isChangeMadeToTitleOrPeriodSection = false;

        const result = await firstValueFrom(getGuardResultAsObservable(hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState)));
        expect(result).toBe(true);
    });

    it('should return true if dismiss warning shall not be displayed', async () => {
        component.shouldDisplayDismissWarning = false;
        component.isChangeMadeToTitleOrPeriodSection = true;

        const result = await firstValueFrom(getGuardResultAsObservable(hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState)));
        expect(result).toBe(true);
    });

    it('should return result from dialog (true, dismiss changes)', async () => {
        component.shouldDisplayDismissWarning = true;

        const resultPromise = TestBed.runInInjectionContext(() => {
            return firstValueFrom(getGuardResultAsObservable(hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState)));
        });

        // Simulate dialog closing with true
        dialogCloseSubject.next(true);
        dialogCloseSubject.complete();

        const result = await resultPromise;
        expect(result).toBe(true);
    });

    it('should return result from dialog (false, keep editing)', async () => {
        component.shouldDisplayDismissWarning = true;

        const resultPromise = TestBed.runInInjectionContext(() => {
            return firstValueFrom(getGuardResultAsObservable(hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState)));
        });

        // Simulate dialog closing with false
        dialogCloseSubject.next(false);
        dialogCloseSubject.complete();

        const result = await resultPromise;
        expect(result).toBe(false);
    });

    it('should return false when dialog is dismissed without result', async () => {
        component.shouldDisplayDismissWarning = true;

        const resultPromise = TestBed.runInInjectionContext(() => {
            return firstValueFrom(getGuardResultAsObservable(hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState)));
        });

        // Simulate dialog dismissal (undefined result)
        dialogCloseSubject.next(undefined);
        dialogCloseSubject.complete();

        const result = await resultPromise;
        expect(result).toBe(false);
    });

    function getGuardResultAsObservable(guardResult: MaybeAsync<GuardResult>): Observable<GuardResult | Promise<GuardResult>> {
        return guardResult instanceof Observable ? guardResult : of(guardResult);
    }
});
