import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { of } from 'rxjs';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import dayjs from 'dayjs/esm';
import { Course } from 'app/core/course/shared/entities/course.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { CourseOverviewGuard } from 'app/core/course/overview/course-overview/course-overview-guard';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { CourseOverviewRoutePath } from 'app/core/course/overview/courses.route';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { User } from 'app/core/user/user.model';

describe('CourseOverviewGuard', () => {
    setupTestBed({ zoneless: true });

    let guard: CourseOverviewGuard;
    let courseStorageService: CourseStorageService;
    let courseManagementService: CourseManagementService;
    let accountService: AccountService;
    let router: Router;

    const visibleRealExam = {
        id: 1,
        visibleDate: dayjs().subtract(1, 'days'),
        startDate: dayjs().subtract(30, 'minutes'),
        testExam: false,
    } as Exam;

    const lecture = new Lecture();

    const mockCourse: Course = { id: 1, lectures: [lecture], exams: [visibleRealExam], numberOfAcceptedFaqs: 3 } as Course;

    const responseFakeCourse = { body: mockCourse } as HttpResponse<Course>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: AccountService, useClass: MockAccountService }, provideHttpClient(), MockProvider(AlertService)],
        });
        guard = TestBed.inject(CourseOverviewGuard);
        courseStorageService = TestBed.inject(CourseStorageService);
        courseManagementService = TestBed.inject(CourseManagementService);
        accountService = TestBed.inject(AccountService);
        router = TestBed.inject(Router);
        vi.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('canActivate', () => {
        it('should return false if courseId is not present', () => {
            const route = { parent: { paramMap: { get: () => undefined } }, routeConfig: { path: CourseOverviewRoutePath.EXERCISES } } as unknown as ActivatedRouteSnapshot;
            let resultValue = true;
            guard.canActivate(route).subscribe((result) => {
                resultValue = result;
            });
            expect(resultValue).toBe(false);
        });

        it('should return true if course is fetched from server', () => {
            const route = { parent: { paramMap: { get: () => '1' } }, routeConfig: { path: CourseOverviewRoutePath.EXERCISES } } as unknown as ActivatedRouteSnapshot;
            let resultValue = false;
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(undefined);
            vi.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of(responseFakeCourse));
            guard.canActivate(route).subscribe((result) => {
                resultValue = result;
            });
            expect(resultValue).toBe(true);
        });
    });

    describe('handleReturn', () => {
        it('should return true if type is lectures and course has lectures', () => {
            let resultValue = true;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.LECTURES);
            result.subscribe((value) => {
                resultValue = value;
            });

            expect(resultValue).toBe(true);
        });

        it('should return true if type is exams and course has visible exams', () => {
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.EXAMS);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBe(true);
        });

        it('should return false if type is exams and course has no visible exams', () => {
            mockCourse.exams = [];
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.EXAMS);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBe(false);
        });

        it('should return true if type is competencies and course has competencies', () => {
            mockCourse.numberOfCompetencies = 1;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.COMPETENCIES);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBe(true);
        });

        it('should return true if type is competencies and course has prerequisits', () => {
            mockCourse.numberOfPrerequisites = 1;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.COMPETENCIES);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBe(true);
        });

        it('should return true if type is tutorial-groups and course has tutorial groups', () => {
            mockCourse.numberOfTutorialGroups = 1;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.TUTORIAL_GROUPS);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBe(true);
        });

        it('should return true if type is dashboard and course has studentCourseAnalyticsDashboardEnabled', () => {
            mockCourse.studentCourseAnalyticsDashboardEnabled = true;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.DASHBOARD);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBe(true);
        });

        it('should return true if type is iris and course has irisEnabledInCourse', () => {
            mockCourse.irisEnabledInCourse = true;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.IRIS);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBe(true);
        });

        it('should return true if type is faq and course has accepted faqs', () => {
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.FAQ);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBe(true);
        });

        it('should return true if type is learning-path and course has learningPathsEnabled', () => {
            mockCourse.learningPathsEnabled = true;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.LEARNING_PATH);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBe(true);
        });

        it('should return false if type is unknown', () => {
            const result = guard.handleReturn(mockCourse, 'unknown');
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBe(false);
        });

        it('should return false if type is dashboard and only iris is enabled', () => {
            mockCourse.studentCourseAnalyticsDashboardEnabled = false;
            mockCourse.irisEnabledInCourse = true;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.DASHBOARD);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBeFalsy();
        });

        it('should redirect to iris when dashboard is denied but iris is enabled', () => {
            mockCourse.studentCourseAnalyticsDashboardEnabled = false;
            mockCourse.irisEnabledInCourse = true;
            const navigateSpy = vi.spyOn(router, 'navigate');
            guard.handleReturn(mockCourse, CourseOverviewRoutePath.DASHBOARD);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/iris']);
        });

        it('should redirect to iris when dashboard is denied, iris is enabled, and the user accepted cloud AI', () => {
            mockCourse.studentCourseAnalyticsDashboardEnabled = false;
            mockCourse.irisEnabledInCourse = true;
            const navigateSpy = vi.spyOn(router, 'navigate');
            guard.handleReturn(mockCourse, CourseOverviewRoutePath.DASHBOARD, { selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/iris']);
        });

        it('should redirect to exercises when dashboard is denied, iris is enabled, but the user opted out of AI', () => {
            mockCourse.studentCourseAnalyticsDashboardEnabled = false;
            mockCourse.irisEnabledInCourse = true;
            const navigateSpy = vi.spyOn(router, 'navigate');
            guard.handleReturn(mockCourse, CourseOverviewRoutePath.DASHBOARD, { selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });

        it('should still grant dashboard access to a user who opted out of AI when the dashboard is enabled', () => {
            mockCourse.studentCourseAnalyticsDashboardEnabled = true;
            mockCourse.irisEnabledInCourse = true;
            const navigateSpy = vi.spyOn(router, 'navigate');
            let resultValue = false;
            guard.handleReturn(mockCourse, CourseOverviewRoutePath.DASHBOARD, { selectedLLMUsage: LLMSelectionDecision.NO_AI } as User).subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBe(true);
            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('should redirect to exercises when dashboard is denied and iris is not enabled', () => {
            mockCourse.studentCourseAnalyticsDashboardEnabled = false;
            mockCourse.irisEnabledInCourse = false;
            const navigateSpy = vi.spyOn(router, 'navigate');
            guard.handleReturn(mockCourse, CourseOverviewRoutePath.DASHBOARD);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });

        it('should navigate to exercises if type is unknown', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            guard.handleReturn(mockCourse, 'unknown');
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });
    });

    describe('canActivate for dashboard path', () => {
        it('should resolve user identity and redirect opted-out users to exercises on cold start', async () => {
            mockCourse.studentCourseAnalyticsDashboardEnabled = false;
            mockCourse.irisEnabledInCourse = true;
            const route = { parent: { paramMap: { get: () => '1' } }, routeConfig: { path: CourseOverviewRoutePath.DASHBOARD } } as unknown as ActivatedRouteSnapshot;
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(mockCourse);
            vi.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of(responseFakeCourse));
            vi.spyOn(accountService, 'identity').mockResolvedValue({ selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);
            const navigateSpy = vi.spyOn(router, 'navigate');

            let resultValue: boolean | undefined;
            await new Promise<void>((resolve) => {
                guard.canActivate(route).subscribe((value) => {
                    resultValue = value;
                    resolve();
                });
            });

            expect(resultValue).toBe(false);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });

        it('should redirect to iris for a non-opted-out user even when identity is only resolved asynchronously', async () => {
            mockCourse.studentCourseAnalyticsDashboardEnabled = false;
            mockCourse.irisEnabledInCourse = true;
            const route = { parent: { paramMap: { get: () => '1' } }, routeConfig: { path: CourseOverviewRoutePath.DASHBOARD } } as unknown as ActivatedRouteSnapshot;
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(mockCourse);
            vi.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of(responseFakeCourse));
            vi.spyOn(accountService, 'identity').mockResolvedValue({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);
            const navigateSpy = vi.spyOn(router, 'navigate');

            await new Promise<void>((resolve) => {
                guard.canActivate(route).subscribe(() => resolve());
            });

            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/iris']);
        });

        it('should not abort the guard stream when identity() rejects; treat unknown user as not opted out', async () => {
            mockCourse.studentCourseAnalyticsDashboardEnabled = false;
            mockCourse.irisEnabledInCourse = true;
            const route = { parent: { paramMap: { get: () => '1' } }, routeConfig: { path: CourseOverviewRoutePath.DASHBOARD } } as unknown as ActivatedRouteSnapshot;
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(mockCourse);
            vi.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of(responseFakeCourse));
            vi.spyOn(accountService, 'identity').mockRejectedValue(new Error('network error'));
            const navigateSpy = vi.spyOn(router, 'navigate');

            let resultValue: boolean | undefined;
            let errored = false;
            await new Promise<void>((resolve) => {
                guard.canActivate(route).subscribe({
                    next: (value) => {
                        resultValue = value;
                    },
                    error: () => {
                        errored = true;
                        resolve();
                    },
                    complete: () => resolve(),
                });
            });

            expect(errored).toBe(false);
            expect(resultValue).toBe(false);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/iris']);
        });
    });
});
