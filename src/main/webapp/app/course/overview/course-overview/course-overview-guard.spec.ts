import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseTabAccess } from 'app/course/shared/entities/course-tab-access.model';
import { provideHttpClient } from '@angular/common/http';
import { CourseOverviewGuard } from 'app/course/overview/course-overview/course-overview-guard';
import { CourseOverviewRoutePath } from 'app/course/overview/courses.route';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/foundation/service/alert.service';

describe('CourseOverviewGuard', () => {
    let guard: CourseOverviewGuard;
    let courseManagementService: CourseManagementService;
    let router: Router;

    /** Access flags for a course where every guarded tab is accessible. */
    const fullAccess: CourseTabAccess = {
        lecturesEnabled: true,
        examsVisible: true,
        competenciesOrPrerequisites: true,
        tutorialGroups: true,
        irisEnabled: true,
        faqAccepted: true,
        learningPathsEnabled: true,
        communicationEnabled: true,
        trainingEnabled: true,
    };

    const route = (path: string, courseId: string | undefined = '1') =>
        ({ parent: { paramMap: { get: () => courseId } }, routeConfig: { path } }) as unknown as ActivatedRouteSnapshot;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: AccountService, useClass: MockAccountService }, provideHttpClient(), MockProvider(AlertService)],
        });
        guard = TestBed.inject(CourseOverviewGuard);
        courseManagementService = TestBed.inject(CourseManagementService);
        router = TestBed.inject(Router);
        vi.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('canActivate', () => {
        it('should return false without fetching when courseId is not present', () => {
            const noCourseRoute = { parent: { paramMap: { get: () => undefined } }, routeConfig: { path: CourseOverviewRoutePath.EXERCISES } } as unknown as ActivatedRouteSnapshot;
            const accessSpy = vi.spyOn(courseManagementService, 'getCourseTabAccess');
            let resultValue = true;
            guard.canActivate(noCourseRoute).subscribe((result) => (resultValue = result));
            expect(resultValue).toBe(false);
            expect(accessSpy).not.toHaveBeenCalled();
        });

        it('should fetch the lightweight access flags once and allow an accessible tab', () => {
            const accessSpy = vi.spyOn(courseManagementService, 'getCourseTabAccess').mockReturnValue(of(fullAccess));
            let resultValue = false;
            guard.canActivate(route(CourseOverviewRoutePath.LECTURES)).subscribe((result) => (resultValue = result));
            expect(accessSpy).toHaveBeenCalledExactlyOnceWith(1);
            expect(resultValue).toBe(true);
        });

        it('should deny and redirect to exercises when the target tab is inaccessible', () => {
            vi.spyOn(courseManagementService, 'getCourseTabAccess').mockReturnValue(of({ lecturesEnabled: false }));
            const navigateSpy = vi.spyOn(router, 'navigate');
            let resultValue = true;
            guard.canActivate(route(CourseOverviewRoutePath.LECTURES)).subscribe((result) => (resultValue = result));
            expect(resultValue).toBe(false);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });

        it('should allow activation when loading the access flags fails (the container then handles the error)', () => {
            vi.spyOn(courseManagementService, 'getCourseTabAccess').mockReturnValue(throwError(() => new Error('network error')));
            let resultValue = false;
            guard.canActivate(route(CourseOverviewRoutePath.LECTURES)).subscribe((result) => (resultValue = result));
            expect(resultValue).toBe(true);
        });
    });

    describe('decideAccess', () => {
        it.each([
            { path: CourseOverviewRoutePath.EXERCISES, access: {} as CourseTabAccess },
            { path: CourseOverviewRoutePath.LECTURES, access: { lecturesEnabled: true } },
            { path: CourseOverviewRoutePath.EXAMS, access: { examsVisible: true } },
            { path: CourseOverviewRoutePath.COMPETENCIES, access: { competenciesOrPrerequisites: true } },
            { path: CourseOverviewRoutePath.TUTORIAL_GROUPS, access: { tutorialGroups: true } },
            { path: CourseOverviewRoutePath.IRIS, access: { irisEnabled: true } },
            { path: CourseOverviewRoutePath.FAQ, access: { faqAccepted: true } },
            { path: CourseOverviewRoutePath.LEARNING_PATH, access: { learningPathsEnabled: true } },
            { path: CourseOverviewRoutePath.COMMUNICATION, access: { communicationEnabled: true } },
            { path: CourseOverviewRoutePath.TRAINING, access: { trainingEnabled: true } },
            { path: CourseOverviewRoutePath.TRAINING_QUIZ, access: { trainingEnabled: true } },
        ])('should grant access to $path when its flag is set', ({ path, access }) => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            expect(guard.decideAccess(1, access, path)).toBe(true);
            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('should deny and redirect to exercises for a guarded tab whose flag is not set', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            expect(guard.decideAccess(1, {}, CourseOverviewRoutePath.EXAMS)).toBe(false);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });

        it('should deny and redirect to exercises for an unknown path', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            expect(guard.decideAccess(1, fullAccess, 'unknown')).toBe(false);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });
    });
});
