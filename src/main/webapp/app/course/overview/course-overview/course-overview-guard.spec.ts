import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import dayjs from 'dayjs/esm';
import { Course } from 'app/course/shared/entities/course.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { CourseOverviewGuard } from 'app/course/overview/course-overview/course-overview-guard';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { CourseOverviewRoutePath } from 'app/course/overview/courses.route';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/foundation/service/alert.service';

describe('CourseOverviewGuard', () => {
    let guard: CourseOverviewGuard;
    let courseStorageService: CourseStorageService;
    let courseManagementService: CourseManagementService;
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

        it('should load the full course once and decide before activation when it is not yet loaded (first navigation into the course)', () => {
            const route = { parent: { paramMap: { get: () => '1' } }, routeConfig: { path: CourseOverviewRoutePath.LECTURES } } as unknown as ActivatedRouteSnapshot;
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(undefined);
            const fetchSpy = vi.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of(responseFakeCourse));
            let resultValue = false;
            guard.canActivate(route).subscribe((result) => {
                resultValue = result;
            });
            // The guard loads the course up-front so an inaccessible tab never mounts; findOneForDashboard stores it for the container to reuse
            expect(fetchSpy).toHaveBeenCalledExactlyOnceWith(1);
            expect(resultValue).toBe(true);
        });

        it('should decide from the stored course without fetching when the full course is already loaded', () => {
            const route = { parent: { paramMap: { get: () => '1' } }, routeConfig: { path: CourseOverviewRoutePath.LECTURES } } as unknown as ActivatedRouteSnapshot;
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(mockCourse);
            vi.spyOn(courseStorageService, 'isCourseFullyLoaded').mockReturnValue(true);
            const fetchSpy = vi.spyOn(courseManagementService, 'findOneForDashboard');
            let resultValue = false;
            guard.canActivate(route).subscribe((result) => {
                resultValue = result;
            });
            expect(resultValue).toBe(true);
            expect(fetchSpy).not.toHaveBeenCalled();
        });

        it('should load the full course and decide when only the slim course from the course list is stored', () => {
            // The course list stores slim courses (e.g. exams and lectures emptied, counters missing); deciding on them
            // would wrongly deny e.g. the "open exam" deep link from the course overview. The guard loads the full
            // course first so the access decision (and the redirect on denial) uses the complete data.
            const route = { parent: { paramMap: { get: () => '1' } }, routeConfig: { path: CourseOverviewRoutePath.EXAMS } } as unknown as ActivatedRouteSnapshot;
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ id: 1, exams: [] } as Course);
            vi.spyOn(courseStorageService, 'isCourseFullyLoaded').mockReturnValue(false);
            const fetchSpy = vi.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of(responseFakeCourse));
            const navigateSpy = vi.spyOn(router, 'navigate');
            let resultValue = false;
            guard.canActivate(route).subscribe((result) => {
                resultValue = result;
            });
            // mockCourse has a visible exam, so EXAMS is accessible
            expect(fetchSpy).toHaveBeenCalledExactlyOnceWith(1);
            expect(resultValue).toBe(true);
            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('should deny and redirect after loading the course when the target route is inaccessible', () => {
            const route = { parent: { paramMap: { get: () => '1' } }, routeConfig: { path: CourseOverviewRoutePath.LECTURES } } as unknown as ActivatedRouteSnapshot;
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(undefined);
            vi.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of({ body: { id: 1 } as Course } as HttpResponse<Course>));
            const navigateSpy = vi.spyOn(router, 'navigate');
            let resultValue = true;
            guard.canActivate(route).subscribe((result) => {
                resultValue = result;
            });
            expect(resultValue).toBe(false);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });

        it('should allow activation when loading the course fails (the container then handles the error)', () => {
            const route = { parent: { paramMap: { get: () => '1' } }, routeConfig: { path: CourseOverviewRoutePath.LECTURES } } as unknown as ActivatedRouteSnapshot;
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(undefined);
            vi.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(throwError(() => new Error('network error')));
            let resultValue = false;
            guard.canActivate(route).subscribe((result) => {
                resultValue = result;
            });
            expect(resultValue).toBe(true);
        });

        it('should deny and redirect based on the stored full course', () => {
            const route = { parent: { paramMap: { get: () => '1' } }, routeConfig: { path: CourseOverviewRoutePath.LECTURES } } as unknown as ActivatedRouteSnapshot;
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ id: 1 } as Course);
            vi.spyOn(courseStorageService, 'isCourseFullyLoaded').mockReturnValue(true);
            const navigateSpy = vi.spyOn(router, 'navigate');
            let resultValue = true;
            guard.canActivate(route).subscribe((result) => {
                resultValue = result;
            });
            expect(resultValue).toBe(false);
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
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

        it('should navigate to exercises if type is unknown', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            guard.handleReturn(mockCourse, 'unknown');
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });
    });
});
