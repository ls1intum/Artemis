import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseAvailableTabs } from 'app/course/shared/entities/course-available-tabs.model';
import { CourseAvailableTabsService } from 'app/course/overview/services/course-available-tabs.service';
import { provideHttpClient } from '@angular/common/http';
import { CourseOverviewGuard } from 'app/course/overview/course-overview/course-overview-guard';
import { CourseOverviewRoutePath } from 'app/course/overview/courses.route';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';

/** Builds a full set of tab flags, overriding the ones a test cares about. */
const tabs = (overrides: Partial<CourseAvailableTabs> = {}): CourseAvailableTabs => ({
    lectures: false,
    exams: false,
    competencies: false,
    tutorialGroups: false,
    iris: false,
    faq: false,
    learningPaths: false,
    communication: false,
    training: false,
    ...overrides,
});

describe('CourseOverviewGuard', () => {
    let guard: CourseOverviewGuard;
    let courseManagementService: CourseManagementService;
    let availableTabsService: CourseAvailableTabsService;
    let router: Router;

    /** Flags for a course where every guarded tab is available. */
    const allTabs = tabs({
        lectures: true,
        exams: true,
        competencies: true,
        tutorialGroups: true,
        iris: true,
        faq: true,
        learningPaths: true,
        communication: true,
        training: true,
    });

    const route = (path: string, courseId: string | undefined = '1') =>
        ({ parent: { paramMap: { get: () => courseId } }, routeConfig: { path } }) as unknown as ActivatedRouteSnapshot;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: AccountService, useClass: MockAccountService }, provideHttpClient(), MockProvider(AlertService)],
        });
        guard = TestBed.inject(CourseOverviewGuard);
        courseManagementService = TestBed.inject(CourseManagementService);
        availableTabsService = TestBed.inject(CourseAvailableTabsService);
        availableTabsService.clear();
        router = TestBed.inject(Router);
        vi.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('canActivate', () => {
        it('should return false without fetching when courseId is not present', () => {
            const noCourseRoute = { parent: { paramMap: { get: () => undefined } }, routeConfig: { path: CourseOverviewRoutePath.EXERCISES } } as unknown as ActivatedRouteSnapshot;
            const fetchSpy = vi.spyOn(courseManagementService, 'getCourseAvailableTabs');
            let resultValue = true;
            guard.canActivate(noCourseRoute).subscribe((result) => (resultValue = result));
            expect(resultValue).toBe(false);
            expect(fetchSpy).not.toHaveBeenCalled();
        });

        it('should fetch the available tabs and allow an available tab', () => {
            const fetchSpy = vi.spyOn(courseManagementService, 'getCourseAvailableTabs').mockReturnValue(of(allTabs));
            let resultValue = false;
            guard.canActivate(route(CourseOverviewRoutePath.LECTURES)).subscribe((result) => (resultValue = result));
            expect(fetchSpy).toHaveBeenCalledExactlyOnceWith(1);
            expect(resultValue).toBe(true);
        });

        it('should not fetch again when the tabs are already held for the course (tab switching is free)', () => {
            const fetchSpy = vi.spyOn(courseManagementService, 'getCourseAvailableTabs').mockReturnValue(of(allTabs));
            guard.canActivate(route(CourseOverviewRoutePath.LECTURES)).subscribe();
            guard.canActivate(route(CourseOverviewRoutePath.EXAMS)).subscribe();
            guard.canActivate(route(CourseOverviewRoutePath.FAQ)).subscribe();
            expect(fetchSpy).toHaveBeenCalledExactlyOnceWith(1);
        });

        it('should fetch again for a different course', () => {
            const fetchSpy = vi.spyOn(courseManagementService, 'getCourseAvailableTabs').mockReturnValue(of(allTabs));
            guard.canActivate(route(CourseOverviewRoutePath.LECTURES, '1')).subscribe();
            guard.canActivate(route(CourseOverviewRoutePath.LECTURES, '2')).subscribe();
            expect(fetchSpy).toHaveBeenCalledTimes(2);
            expect(fetchSpy).toHaveBeenLastCalledWith(2);
        });

        it('should deny and redirect to exercises when the target tab is unavailable', () => {
            vi.spyOn(courseManagementService, 'getCourseAvailableTabs').mockReturnValue(of(tabs({ lectures: false })));
            const navigateSpy = vi.spyOn(router, 'navigate');
            let resultValue = true;
            guard.canActivate(route(CourseOverviewRoutePath.LECTURES)).subscribe((result) => (resultValue = result));
            expect(resultValue).toBe(false);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });

        it('should allow activation when loading the available tabs fails (the container then handles the error)', () => {
            vi.spyOn(courseManagementService, 'getCourseAvailableTabs').mockReturnValue(throwError(() => new Error('network error')));
            let resultValue = false;
            guard.canActivate(route(CourseOverviewRoutePath.LECTURES)).subscribe((result) => (resultValue = result));
            expect(resultValue).toBe(true);
        });
    });

    describe('decideAccess', () => {
        it.each([
            { path: CourseOverviewRoutePath.EXERCISES, available: tabs() },
            { path: CourseOverviewRoutePath.LECTURES, available: tabs({ lectures: true }) },
            { path: CourseOverviewRoutePath.EXAMS, available: tabs({ exams: true }) },
            { path: CourseOverviewRoutePath.COMPETENCIES, available: tabs({ competencies: true }) },
            { path: CourseOverviewRoutePath.TUTORIAL_GROUPS, available: tabs({ tutorialGroups: true }) },
            { path: CourseOverviewRoutePath.IRIS, available: tabs({ iris: true }) },
            { path: CourseOverviewRoutePath.FAQ, available: tabs({ faq: true }) },
            { path: CourseOverviewRoutePath.LEARNING_PATH, available: tabs({ learningPaths: true }) },
            { path: CourseOverviewRoutePath.COMMUNICATION, available: tabs({ communication: true }) },
            { path: CourseOverviewRoutePath.TRAINING, available: tabs({ training: true }) },
            { path: CourseOverviewRoutePath.TRAINING_QUIZ, available: tabs({ training: true }) },
        ])('should grant access to $path when its flag is set', ({ path, available }) => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            expect(guard.decideAccess(1, available, path)).toBe(true);
            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('should deny and redirect to exercises for a guarded tab whose flag is not set', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            expect(guard.decideAccess(1, tabs(), CourseOverviewRoutePath.EXAMS)).toBe(false);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });

        it('should deny and redirect to exercises for an unknown path', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            expect(guard.decideAccess(1, allTabs, 'unknown')).toBe(false);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });
    });
});
