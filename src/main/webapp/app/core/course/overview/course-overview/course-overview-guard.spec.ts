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

    const mockCourse: Course = { id: 1, lectures: [lecture], exams: [visibleRealExam], faqEnabled: true } as Course;

    const responseFakeCourse = { body: mockCourse } as HttpResponse<Course>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: AccountService, useClass: MockAccountService }, provideHttpClient(), MockProvider(AlertService)],
        });
        guard = TestBed.inject(CourseOverviewGuard);
        courseStorageService = TestBed.inject(CourseStorageService);
        courseManagementService = TestBed.inject(CourseManagementService);
        router = TestBed.inject(Router);
    });

    describe('canActivate', () => {
        it('should return false if courseId is not present', () => {
            const route = { parent: { paramMap: { get: () => undefined } }, routeConfig: { path: CourseOverviewRoutePath.EXERCISES } } as unknown as ActivatedRouteSnapshot;
            let resultValue = true;
            guard.canActivate(route).subscribe((result) => {
                resultValue = result;
            });
            expect(resultValue).toBeFalse();
        });

        it('should return true if course is fetched from server', () => {
            const route = { parent: { paramMap: { get: () => '1' } }, routeConfig: { path: CourseOverviewRoutePath.EXERCISES } } as unknown as ActivatedRouteSnapshot;
            let resultValue = false;
            jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(undefined);
            jest.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of(responseFakeCourse));
            guard.canActivate(route).subscribe((result) => {
                resultValue = result;
            });
            expect(resultValue).toBeTrue();
        });
    });

    describe('handleReturn', () => {
        it('should return true if type is lectures and course has lectures', () => {
            let resultValue = true;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.LECTURES);
            result.subscribe((value) => {
                resultValue = value;
            });

            expect(resultValue).toBeTrue();
        });

        it('should return true if type is exams and course has visible exams', () => {
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.EXAMS);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBeTrue();
        });

        it('should return false if type is exams and course has no visible exams', () => {
            mockCourse.exams = [];
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.EXAMS);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBeFalse();
        });

        it('should return true if type is competencies and course has competencies', () => {
            mockCourse.numberOfCompetencies = 1;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.COMPETENCIES);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBeTrue();
        });

        it('should return true if type is competencies and course has prerequisits', () => {
            mockCourse.numberOfPrerequisites = 1;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.COMPETENCIES);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBeTrue();
        });

        it('should return true if type is tutorial-groups and course has tutorial groups', () => {
            mockCourse.numberOfTutorialGroups = 1;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.TUTORIAL_GROUPS);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBeTrue();
        });

        it('should return true if type is dashboard and course has studentCourseAnalyticsDashboardEnabled', () => {
            mockCourse.studentCourseAnalyticsDashboardEnabled = true;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.DASHBOARD);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBeTrue();
        });

        it('should return true if type is iris and course has irisEnabledInCourse', () => {
            mockCourse.irisEnabledInCourse = true;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.IRIS);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBeTrue();
        });

        it('should return true if type is faq and course has faqEnabled', () => {
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.FAQ);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBeTrue();
        });

        it('should return true if type is learning-path and course has learningPathsEnabled', () => {
            mockCourse.learningPathsEnabled = true;
            const result = guard.handleReturn(mockCourse, CourseOverviewRoutePath.LEARNING_PATH);
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBeTrue();
        });

        it('should return false if type is unknown', () => {
            const result = guard.handleReturn(mockCourse, 'unknown');
            let resultValue = true;
            result.subscribe((value) => {
                resultValue = value;
            });
            expect(resultValue).toBeFalse();
        });

        it('should navigate to exercises if type is unknown', () => {
            const navigateSpy = jest.spyOn(router, 'navigate');
            guard.handleReturn(mockCourse, 'unknown');
            expect(navigateSpy).toHaveBeenCalledWith(['/courses/1/exercises']);
        });
    });
});
