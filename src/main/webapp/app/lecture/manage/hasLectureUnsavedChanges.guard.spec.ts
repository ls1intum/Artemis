import { ActivatedRouteSnapshot, GuardResult, MaybeAsync, Router, RouterStateSnapshot } from '@angular/router';
import { LectureUnsavedChangesComponent, hasLectureUnsavedChangesGuard } from 'app/lecture/manage/hasLectureUnsavedChanges.guard';
import { TestBed } from '@angular/core/testing';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Observable, firstValueFrom, of } from 'rxjs';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';

describe('hasLectureUnsavedChanges', () => {
    let component: LectureUnsavedChangesComponent;
    let currentRoute: ActivatedRouteSnapshot;
    let currentState: RouterStateSnapshot;
    let nextState: RouterStateSnapshot;
    let mockNgbModal: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();

        component = {
            shouldDisplayDismissWarning: true,
            isChangeMadeToTitleSection: jest.fn().mockReturnValue(true),
            isChangeMadeToPeriodSection: jest.fn().mockReturnValue(true),
            isChangeMadeToTitleOrPeriodSection: true,
        };
        mockNgbModal = TestBed.inject(NgbModal);
        const mockModalRef = {
            componentInstance: {},
            result: Promise.resolve(true),
        };
        jest.spyOn(mockNgbModal, 'open').mockReturnValue(mockModalRef as NgbModalRef);

        currentRoute = {} as ActivatedRouteSnapshot;
        currentState = {} as RouterStateSnapshot;
        nextState = {} as RouterStateSnapshot;
    });

    it('should return true if warning is not bypassed by shouldDisplayDismissWarning variable but no changes were made', async () => {
        component.shouldDisplayDismissWarning = true;
        component.isChangeMadeToTitleOrPeriodSection = false;

        const result = await firstValueFrom(getGuardResultAsObservable(hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState)));
        expect(result).toBeTrue();
    });

    it('should return true if dismiss warning shall not be displayed', async () => {
        component.shouldDisplayDismissWarning = false;
        component.isChangeMadeToTitleOrPeriodSection = true;

        const result = await firstValueFrom(getGuardResultAsObservable(hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState)));
        expect(result).toBeTrue();
    });

    it('should return result from modal (true, dismiss changes)', async () => {
        component.shouldDisplayDismissWarning = true;

        const result = await TestBed.runInInjectionContext(() => {
            return firstValueFrom(getGuardResultAsObservable(hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState)));
        });

        expect(result).toBeTrue();
    });

    it('should return result from modal (false, keep editing)', async () => {
        component.shouldDisplayDismissWarning = true;

        const mockModalRef = {
            componentInstance: {},
            result: Promise.resolve(false),
        };
        jest.spyOn(mockNgbModal, 'open').mockReturnValue(mockModalRef as NgbModalRef);

        const result = await TestBed.runInInjectionContext(() => {
            return firstValueFrom(getGuardResultAsObservable(hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState)));
        });

        expect(result).toBeFalse();
    });

    function getGuardResultAsObservable(guardResult: MaybeAsync<GuardResult>): Observable<GuardResult | Promise<GuardResult>> {
        return guardResult instanceof Observable ? guardResult : of(guardResult);
    }
});
