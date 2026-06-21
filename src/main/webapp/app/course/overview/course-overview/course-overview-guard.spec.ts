import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { User } from 'app/account/user/user.model';

describe('CourseOverviewGuard', () => {
    setupTestBed({ zoneless: true });

    let guard: CourseOverviewGuard;
    let courseManagementService: CourseManagementService;
    let accountService: AccountService;
    let router: Router;

    /** Access flags for a course where every guarded tab is accessible. */
    const fullAccess: CourseTabAccess = {
        lecturesEnabled: true,
        examsVisible: true,
        competenciesOrPrerequisites: true,
        tutorialGroups: true,
        dashboardEnabled: true,
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
        accountService = TestBed.inject(AccountService);
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

        it('should resolve user identity and redirect an opted-out user away from a denied dashboard to exercises', async () => {
            vi.spyOn(courseManagementService, 'getCourseTabAccess').mockReturnValue(of({ dashboardEnabled: false, irisEnabled: true }));
            vi.spyOn(accountService, 'identity').mockResolvedValue({ selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);
            const navigateSpy = vi.spyOn(router, 'navigate');

            let resultValue: boolean | undefined;
            await new Promise<void>((resolve) => guard.canActivate(route(CourseOverviewRoutePath.DASHBOARD)).subscribe((value) => ((resultValue = value), resolve())));

            expect(resultValue).toBe(false);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });

        it('should redirect a non-opted-out user from a denied dashboard to iris (identity resolved asynchronously)', async () => {
            vi.spyOn(courseManagementService, 'getCourseTabAccess').mockReturnValue(of({ dashboardEnabled: false, irisEnabled: true }));
            vi.spyOn(accountService, 'identity').mockResolvedValue({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);
            const navigateSpy = vi.spyOn(router, 'navigate');

            await new Promise<void>((resolve) => guard.canActivate(route(CourseOverviewRoutePath.DASHBOARD)).subscribe(() => resolve()));

            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/iris']);
        });

        it('should not abort the guard stream when identity() rejects; treat unknown user as not opted out', async () => {
            vi.spyOn(courseManagementService, 'getCourseTabAccess').mockReturnValue(of({ dashboardEnabled: false, irisEnabled: true }));
            vi.spyOn(accountService, 'identity').mockRejectedValue(new Error('network error'));
            const navigateSpy = vi.spyOn(router, 'navigate');

            let resultValue: boolean | undefined;
            let errored = false;
            await new Promise<void>((resolve) => {
                guard.canActivate(route(CourseOverviewRoutePath.DASHBOARD)).subscribe({
                    next: (value) => (resultValue = value),
                    error: () => ((errored = true), resolve()),
                    complete: () => resolve(),
                });
            });

            expect(errored).toBe(false);
            expect(resultValue).toBe(false);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/iris']);
        });
    });

    describe('decideAccess', () => {
        it.each([
            { path: CourseOverviewRoutePath.EXERCISES, access: {} as CourseTabAccess },
            { path: CourseOverviewRoutePath.LECTURES, access: { lecturesEnabled: true } },
            { path: CourseOverviewRoutePath.EXAMS, access: { examsVisible: true } },
            { path: CourseOverviewRoutePath.COMPETENCIES, access: { competenciesOrPrerequisites: true } },
            { path: CourseOverviewRoutePath.TUTORIAL_GROUPS, access: { tutorialGroups: true } },
            { path: CourseOverviewRoutePath.DASHBOARD, access: { dashboardEnabled: true } },
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

        it('should redirect to iris when dashboard is denied but iris is enabled (no user)', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            guard.decideAccess(1, { dashboardEnabled: false, irisEnabled: true }, CourseOverviewRoutePath.DASHBOARD);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/iris']);
        });

        it('should redirect to iris when dashboard is denied, iris is enabled, and the user accepted cloud AI', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            guard.decideAccess(1, { dashboardEnabled: false, irisEnabled: true }, CourseOverviewRoutePath.DASHBOARD, { selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/iris']);
        });

        it('should redirect to exercises when dashboard is denied, iris is enabled, but the user opted out of AI', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            guard.decideAccess(1, { dashboardEnabled: false, irisEnabled: true }, CourseOverviewRoutePath.DASHBOARD, { selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });

        it('should grant dashboard access to a user who opted out of AI when the dashboard is enabled', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            expect(
                guard.decideAccess(1, { dashboardEnabled: true, irisEnabled: true }, CourseOverviewRoutePath.DASHBOARD, { selectedLLMUsage: LLMSelectionDecision.NO_AI } as User),
            ).toBe(true);
            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('should redirect to exercises when dashboard is denied and iris is not enabled', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            guard.decideAccess(1, { dashboardEnabled: false, irisEnabled: false }, CourseOverviewRoutePath.DASHBOARD);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });
    });
});
