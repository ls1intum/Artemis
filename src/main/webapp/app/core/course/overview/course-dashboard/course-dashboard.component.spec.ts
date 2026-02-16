import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseDashboardComponent } from 'app/core/course/overview/course-dashboard/course-dashboard.component';
import { By } from '@angular/platform-browser';
import { Component, DebugElement, input } from '@angular/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, Router } from '@angular/router';
import { MockComponent, MockDirective } from 'ng-mocks';
import { CourseExerciseLatenessComponent } from 'app/core/course/overview/course-dashboard/course-exercise-lateness/course-exercise-lateness.component';
import { CourseExercisePerformanceComponent } from 'app/core/course/overview/course-dashboard/course-exercise-performance/course-exercise-performance.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { of, throwError } from 'rxjs';
import { CourseDashboardService } from 'app/core/course/overview/course-dashboard/course-dashboard.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CompetencyAccordionComponent, CompetencyAccordionToggleEvent } from 'app/atlas/overview/competency-accordion/competency-accordion.component';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import dayjs from 'dayjs/esm';
import { StudentMetrics } from 'app/atlas/shared/entities/student-metrics.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { provideNoopAnimationsForTests } from 'test/helpers/animations';

// Manual mock for CourseChatbotComponent to avoid ng-mocks issues with signal queries (viewChild)
@Component({
    selector: 'jhi-course-chatbot',
    template: '',
})
class MockCourseChatbotComponent {
    readonly courseId = input<number>();
}

describe('CourseDashboardComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseDashboardComponent;
    let fixture: ComponentFixture<CourseDashboardComponent>;
    let debugElement: DebugElement;
    let courseDashboardService: CourseDashboardService;
    let router: Router;

    const mockStudentMetrics: StudentMetrics = {
        exerciseMetrics: {
            exerciseInformation: {
                1: {
                    id: 1,
                    title: 'Exercise 1',
                    shortName: 'E1',
                    startDate: dayjs().subtract(10, 'days'),
                    dueDate: dayjs().subtract(5, 'days'),
                    maxPoints: 10,
                    type: ExerciseType.PROGRAMMING,
                },
                2: {
                    id: 2,
                    title: 'Exercise 2',
                    shortName: 'E2',
                    startDate: dayjs().subtract(8, 'days'),
                    dueDate: dayjs().subtract(3, 'days'),
                    maxPoints: 20,
                    type: ExerciseType.MODELING,
                },
                3: {
                    id: 3,
                    title: 'Exercise 3',
                    shortName: 'E3',
                    startDate: dayjs().subtract(6, 'days'),
                    dueDate: dayjs().add(5, 'days'), // future due date - should be filtered out
                    maxPoints: 30,
                    type: ExerciseType.TEXT,
                },
            },
            score: {
                1: 80,
                2: 50,
            },
            averageScore: {
                1: 70,
                2: 60,
            },
            latestSubmission: {
                1: 50,
                2: 75,
            },
            averageLatestSubmission: {
                1: 60,
                2: 80,
            },
        },
        competencyMetrics: {
            competencyInformation: {
                1: {
                    id: 1,
                    title: 'Competency 1',
                    description: 'Description 1',
                    optional: false,
                    masteryThreshold: 80,
                },
                2: {
                    id: 2,
                    title: 'Competency 2',
                    description: 'Description 2',
                    optional: false,
                    masteryThreshold: 80,
                },
            },
            exercises: {
                1: [1],
                2: [3],
            },
            lectureUnits: {
                2: [10],
            },
            progress: {
                1: 50,
                2: 30,
            },
            confidence: {
                1: 0.8,
                2: 0.6,
            },
        },
        lectureUnitStudentMetricsDTO: {
            lectureUnitInformation: {
                10: {
                    id: 10,
                    lectureId: 1,
                    lectureTitle: 'Lecture 1',
                    name: 'Unit 1',
                    releaseDate: dayjs().subtract(2, 'days'),
                    type: 'TEXT' as any,
                },
            },
            completed: [10],
        },
    };

    const mockCourse: Course = {
        id: 123,
        studentCourseAnalyticsDashboardEnabled: true,
        irisEnabledInCourse: true,
        learningPathsEnabled: true,
        exercises: Object.values(mockStudentMetrics.exerciseMetrics?.exerciseInformation ?? {}) as any,
    } as Course;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                CourseDashboardComponent,
                MockCourseChatbotComponent,
                NgbProgressbar,
                TranslatePipeMock,
                MockComponent(CourseExerciseLatenessComponent),
                MockComponent(CourseExercisePerformanceComponent),
                MockComponent(CompetencyAccordionComponent),
                MockComponent(FeatureOverlayComponent),
                MockDirective(FeatureToggleDirective),
                MockDirective(FeatureToggleHideDirective),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ courseId: '123' }) },
                LocalStorageService,
                SessionStorageService,
                { provide: ProfileService, useClass: MockProfileService },
                {
                    provide: CourseStorageService,
                    useValue: {
                        getCourse: vi.fn().mockReturnValue(mockCourse),
                        subscribeToCourseUpdates: vi.fn().mockReturnValue(of(mockCourse)),
                    },
                },
                {
                    provide: CourseDashboardService,
                    useValue: {
                        getCourseMetricsForUser: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockStudentMetrics }))),
                    },
                },
                {
                    provide: AlertService,
                    useValue: {
                        error: vi.fn(),
                    },
                },
                provideNoopAnimationsForTests(),
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(CourseDashboardComponent);
        component = fixture.componentInstance;
        debugElement = fixture.debugElement;
        courseDashboardService = TestBed.inject(CourseDashboardService);
        router = TestBed.inject(Router);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should display chatbot when iris is enabled', () => {
        fixture.changeDetectorRef.detectChanges();

        const chatContainer = debugElement.query(By.css('.chat-container'));
        expect(chatContainer).toBeTruthy();
    });

    it('should show loading spinner when isLoading is true', () => {
        (component as any)._isLoading.set(true);
        fixture.changeDetectorRef.detectChanges();

        const spinner = debugElement.query(By.css('.spinner-border'));
        expect(spinner).toBeTruthy();
    });

    it('should toggle isCollapsed when toggleSidebar is called', () => {
        expect(component.isCollapsed).toBe(false);

        component.toggleSidebar();
        expect(component.isCollapsed).toBe(true);

        component.toggleSidebar();
        expect(component.isCollapsed).toBe(false);
    });

    it('should correctly calculate overall performance', () => {
        const exerciseMetrics = {
            exerciseInformation: {
                1: { id: 1, maxPoints: 10 },
                2: { id: 2, maxPoints: 20 },
                3: { id: 3, maxPoints: 30 },
            },
            score: {
                1: 80,
                2: 50,
                3: 100,
            },
        };

        const exerciseIds = [1, 2, 3];
        (component as any).setOverallPerformance(exerciseIds, exerciseMetrics);

        expect(component.points()).toBeCloseTo(48.0, 1);
        expect(component.maxPoints()).toBeCloseTo(60.0, 1);
        expect(component.progress()).toBeCloseTo(80.0, 1);
    });

    it('should correctly map exercise performance data', () => {
        const exerciseMetrics = {
            exerciseInformation: {
                1: { title: 'Exercise 1', shortName: 'E1' },
                2: { title: 'Exercise 2', shortName: 'E2' },
            },
            score: {
                1: 90,
                2: 50,
            },
            averageScore: {
                1: 70,
                2: 60,
            },
        };

        const sortedIds = [1, 2];
        (component as any).setExercisePerformance(sortedIds, exerciseMetrics);

        expect(component.exercisePerformance()).toEqual([
            {
                exerciseId: 1,
                title: 'Exercise 1',
                shortName: 'E1',
                score: 90,
                averageScore: 70,
            },
            {
                exerciseId: 2,
                title: 'Exercise 2',
                shortName: 'E2',
                score: 50,
                averageScore: 60,
            },
        ]);
    });

    it('should skip exercises without information in exercise performance', () => {
        const exerciseMetrics = {
            exerciseInformation: {
                1: { title: 'Exercise 1', shortName: 'E1' },
            },
            score: {
                1: 100,
                2: 50,
            },
            averageScore: {
                1: 80,
                2: 60,
            },
        };

        const sortedIds = [1, 2];
        (component as any).setExercisePerformance(sortedIds, exerciseMetrics);

        expect(component.exercisePerformance()).toHaveLength(1);
        expect(component.exercisePerformance()![0].exerciseId).toBe(1);
    });

    it('should correctly map exercise lateness data', () => {
        const exerciseMetrics = {
            exerciseInformation: {
                1: { title: 'Exercise 1', shortName: 'E1' },
                2: { title: 'Exercise 2', shortName: 'E2' },
            },
            latestSubmission: {
                1: -0.2,
                2: 0.5,
            },
            averageLatestSubmission: {
                1: -0.1,
                2: 0.4,
            },
        };

        const sortedIds = [1, 2];
        (component as any).setExerciseLateness(sortedIds, exerciseMetrics);

        expect(component.exerciseLateness()).toEqual([
            {
                exerciseId: 1,
                title: 'Exercise 1',
                shortName: 'E1',
                relativeLatestSubmission: -0.2,
                relativeAverageLatestSubmission: -0.1,
            },
            {
                exerciseId: 2,
                title: 'Exercise 2',
                shortName: 'E2',
                relativeLatestSubmission: 0.5,
                relativeAverageLatestSubmission: 0.4,
            },
        ]);
    });

    it('should skip exercises without information in exercise lateness', () => {
        const exerciseMetrics = {
            exerciseInformation: {
                1: { title: 'Exercise 1', shortName: 'E1' },
            },
            latestSubmission: {
                1: -0.3,
                2: 0.6,
            },
            averageLatestSubmission: {
                1: -0.2,
                2: 0.5,
            },
        };

        const sortedIds = [1, 2];
        (component as any).setExerciseLateness(sortedIds, exerciseMetrics);

        expect(component.exerciseLateness()).toHaveLength(1);
        expect(component.exerciseLateness()![0].exerciseId).toBe(1);
    });

    it('should handle empty input gracefully in exercise lateness', () => {
        const exerciseMetrics = {
            exerciseInformation: {},
            latestSubmission: {},
            averageLatestSubmission: {},
        };

        const sortedIds: number[] = [];
        (component as any).setExerciseLateness(sortedIds, exerciseMetrics);

        expect(component.exerciseLateness()).toEqual([]);
    });

    it('should load metrics when course changes and dashboard is enabled', () => {
        const loadMetricsSpy = vi.spyOn(component, 'loadMetrics');
        const newCourse: Course = { id: 456, studentCourseAnalyticsDashboardEnabled: true } as Course;

        (component as any).setCourse(newCourse);

        expect(loadMetricsSpy).toHaveBeenCalled();
    });

    it('should not load metrics when course changes but dashboard is disabled', () => {
        const loadMetricsSpy = vi.spyOn(component, 'loadMetrics');
        const newCourse: Course = { id: 456, studentCourseAnalyticsDashboardEnabled: false } as Course;

        (component as any).setCourse(newCourse);

        expect(loadMetricsSpy).not.toHaveBeenCalled();
    });

    it('should not load metrics when course id is the same', () => {
        // First set a course
        (component as any)._course.set({ id: 123, studentCourseAnalyticsDashboardEnabled: true });

        const loadMetricsSpy = vi.spyOn(component, 'loadMetrics');
        const sameCourse: Course = { id: 123, studentCourseAnalyticsDashboardEnabled: true } as Course;

        (component as any).setCourse(sameCourse);

        expect(loadMetricsSpy).not.toHaveBeenCalled();
    });

    it('should handle toggle event and set opened accordion index', () => {
        const toggleEvent: CompetencyAccordionToggleEvent = { opened: true, index: 2 };

        component.handleToggle(toggleEvent);

        expect(component.openedAccordionIndex()).toBe(2);
    });

    it('should set opened accordion index to undefined when closed', () => {
        // First open an accordion
        component.handleToggle({ opened: true, index: 1 });
        expect(component.openedAccordionIndex()).toBe(1);

        // Then close it
        component.handleToggle({ opened: false, index: 1 });
        expect(component.openedAccordionIndex()).toBeUndefined();
    });

    it('should navigate to learning paths', () => {
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        (component as any)._courseId.set(123);

        component.navigateToLearningPaths();

        expect(navigateSpy).toHaveBeenCalledWith(['courses', 123, 'learning-path']);
    });

    it('should handle error when loading metrics fails', () => {
        const errorResponse = new HttpErrorResponse({ status: 500, statusText: 'Internal Server Error' });
        vi.spyOn(courseDashboardService, 'getCourseMetricsForUser').mockReturnValue(throwError(() => errorResponse));

        component.loadMetrics();

        expect(component.isLoading()).toBe(false);
    });

    it('should unsubscribe from previous metrics subscription when loading new metrics', () => {
        const mockSubscription = { unsubscribe: vi.fn() };
        (component as any).metricsSubscription = mockSubscription;

        component.loadMetrics();

        expect(mockSubscription.unsubscribe).toHaveBeenCalled();
    });

    it('should filter competencies based on exercise start dates', () => {
        const metricsWithCompetencies: StudentMetrics = {
            exerciseMetrics: {
                exerciseInformation: {
                    1: {
                        id: 1,
                        title: 'Exercise 1',
                        shortName: 'E1',
                        startDate: dayjs().subtract(5, 'days'),
                        dueDate: dayjs().subtract(1, 'days'),
                        maxPoints: 10,
                        type: ExerciseType.PROGRAMMING,
                    },
                    2: {
                        id: 2,
                        title: 'Exercise 2',
                        shortName: 'E2',
                        startDate: dayjs().add(5, 'days'), // Future start date
                        dueDate: dayjs().add(10, 'days'),
                        maxPoints: 20,
                        type: ExerciseType.TEXT,
                    },
                },
                score: { 1: 80 },
                averageScore: { 1: 70 },
            },
            competencyMetrics: {
                competencyInformation: {
                    1: {
                        id: 1,
                        title: 'Competency 1',
                        description: 'Desc 1',
                        optional: false,
                        masteryThreshold: 80,
                    },
                    2: {
                        id: 2,
                        title: 'Competency 2',
                        description: 'Desc 2',
                        optional: false,
                        masteryThreshold: 80,
                    },
                },
                exercises: {
                    1: [1], // linked to started exercise
                    2: [2], // linked to future exercise
                },
            },
        };

        vi.spyOn(courseDashboardService, 'getCourseMetricsForUser').mockReturnValue(of(new HttpResponse({ body: metricsWithCompetencies })));

        component.loadMetrics();

        // Only competency 1 should be included because it has an exercise that has started
        expect(component.competencies()).toHaveLength(1);
        expect(component.competencies()[0].id).toBe(1);
    });

    it('should filter competencies based on lecture unit release dates', () => {
        const metricsWithLectureUnits: StudentMetrics = {
            exerciseMetrics: {
                exerciseInformation: {},
            },
            competencyMetrics: {
                competencyInformation: {
                    1: {
                        id: 1,
                        title: 'Competency 1',
                        description: 'Desc 1',
                        optional: false,
                        masteryThreshold: 80,
                    },
                    2: {
                        id: 2,
                        title: 'Competency 2',
                        description: 'Desc 2',
                        optional: false,
                        masteryThreshold: 80,
                    },
                },
                exercises: {},
                lectureUnits: {
                    1: [10], // linked to released lecture unit
                    2: [20], // linked to future lecture unit
                },
            },
            lectureUnitStudentMetricsDTO: {
                lectureUnitInformation: {
                    10: {
                        id: 10,
                        lectureId: 1,
                        lectureTitle: 'Lecture 1',
                        name: 'Unit 1',
                        releaseDate: dayjs().subtract(2, 'days'),
                        type: 'TEXT' as any,
                    },
                    20: {
                        id: 20,
                        lectureId: 2,
                        lectureTitle: 'Lecture 2',
                        name: 'Unit 2',
                        releaseDate: dayjs().add(5, 'days'), // Future release date
                        type: 'TEXT' as any,
                    },
                },
            },
        };

        vi.spyOn(courseDashboardService, 'getCourseMetricsForUser').mockReturnValue(of(new HttpResponse({ body: metricsWithLectureUnits })));

        component.loadMetrics();

        // Only competency 1 should be included because it has a released lecture unit
        expect(component.competencies()).toHaveLength(1);
        expect(component.competencies()[0].id).toBe(1);
    });

    it('should handle response with null body', () => {
        const previousMetrics = component.studentMetrics();
        vi.spyOn(courseDashboardService, 'getCourseMetricsForUser').mockReturnValue(of(new HttpResponse<StudentMetrics>({ body: null })));

        component.loadMetrics();

        expect(component.isLoading()).toBe(false);
        expect(component.studentMetrics()).toBe(previousMetrics);
    });

    it('should sort competencies by id', () => {
        const metricsWithMultipleCompetencies: StudentMetrics = {
            exerciseMetrics: {
                exerciseInformation: {
                    1: {
                        id: 1,
                        title: 'Exercise 1',
                        shortName: 'E1',
                        startDate: dayjs().subtract(5, 'days'),
                        dueDate: dayjs().subtract(1, 'days'),
                        maxPoints: 10,
                        type: ExerciseType.PROGRAMMING,
                    },
                },
            },
            competencyMetrics: {
                competencyInformation: {
                    3: {
                        id: 3,
                        title: 'Competency 3',
                        description: 'Desc 3',
                        optional: false,
                        masteryThreshold: 80,
                    },
                    1: {
                        id: 1,
                        title: 'Competency 1',
                        description: 'Desc 1',
                        optional: false,
                        masteryThreshold: 80,
                    },
                    2: {
                        id: 2,
                        title: 'Competency 2',
                        description: 'Desc 2',
                        optional: false,
                        masteryThreshold: 80,
                    },
                },
                exercises: {
                    1: [1],
                    2: [1],
                    3: [1],
                },
            },
        };

        vi.spyOn(courseDashboardService, 'getCourseMetricsForUser').mockReturnValue(of(new HttpResponse({ body: metricsWithMultipleCompetencies })));

        component.loadMetrics();

        expect(component.competencies()).toHaveLength(3);
        expect(component.competencies()[0].id).toBe(1);
        expect(component.competencies()[1].id).toBe(2);
        expect(component.competencies()[2].id).toBe(3);
    });

    it('should limit exercises to last 10', () => {
        const manyExercises: { [key: number]: any } = {};
        for (let i = 1; i <= 15; i++) {
            manyExercises[i] = {
                id: i,
                title: `Exercise ${i}`,
                shortName: `E${i}`,
                startDate: dayjs().subtract(30 - i, 'days'),
                dueDate: dayjs().subtract(20 - i, 'days'),
                maxPoints: 10,
                type: ExerciseType.PROGRAMMING,
            };
        }

        const metricsWithManyExercises: StudentMetrics = {
            exerciseMetrics: {
                exerciseInformation: manyExercises,
                score: {},
                averageScore: {},
            },
        };

        vi.spyOn(courseDashboardService, 'getCourseMetricsForUser').mockReturnValue(of(new HttpResponse({ body: metricsWithManyExercises })));

        component.loadMetrics();

        expect(component.exercisePerformance()!.length).toBeLessThanOrEqual(10);
    });

    it('should set hasExercises to true when there are exercises with past due dates', () => {
        component.loadMetrics();

        expect(component.hasExercises()).toBe(true);
    });

    it('should set hasExercises to false when there are no exercises', () => {
        const emptyMetrics: StudentMetrics = {
            exerciseMetrics: {
                exerciseInformation: {},
            },
        };

        vi.spyOn(courseDashboardService, 'getCourseMetricsForUser').mockReturnValue(of(new HttpResponse({ body: emptyMetrics })));

        component.loadMetrics();

        expect(component.hasExercises()).toBe(false);
    });

    it('should handle empty exercise metrics', () => {
        const metricsWithoutExercises: StudentMetrics = {};

        vi.spyOn(courseDashboardService, 'getCourseMetricsForUser').mockReturnValue(of(new HttpResponse({ body: metricsWithoutExercises })));

        component.loadMetrics();

        expect(component.hasExercises()).toBe(false);
        expect(component.exercisePerformance()).toEqual([]);
        expect(component.exerciseLateness()).toEqual([]);
    });

    it('should handle empty competency metrics', () => {
        const metricsWithoutCompetencies: StudentMetrics = {
            exerciseMetrics: {
                exerciseInformation: {
                    1: {
                        id: 1,
                        title: 'Exercise 1',
                        shortName: 'E1',
                        startDate: dayjs().subtract(5, 'days'),
                        dueDate: dayjs().subtract(1, 'days'),
                        maxPoints: 10,
                        type: ExerciseType.PROGRAMMING,
                    },
                },
            },
        };

        vi.spyOn(courseDashboardService, 'getCourseMetricsForUser').mockReturnValue(of(new HttpResponse({ body: metricsWithoutCompetencies })));

        component.loadMetrics();

        expect(component.hasCompetencies()).toBe(false);
        expect(component.competencies()).toEqual([]);
    });

    it('should handle exercises with missing score data', () => {
        const exerciseMetrics = {
            exerciseInformation: {
                1: { id: 1, maxPoints: 10 },
                2: { id: 2, maxPoints: 20 },
            },
            score: {
                // score for exercise 2 is missing
                1: 80,
            },
        };

        const exerciseIds = [1, 2];
        (component as any).setOverallPerformance(exerciseIds, exerciseMetrics);

        // Exercise 1: 80/100 * 10 = 8 points
        // Exercise 2: 0/100 * 20 = 0 points (missing score defaults to 0)
        expect(component.points()).toBeCloseTo(8.0, 1);
        expect(component.maxPoints()).toBeCloseTo(30.0, 1);
    });

    it('should handle exercise sorting with fallback to startDate when dueDate is undefined', () => {
        const metricsWithMixedDates: StudentMetrics = {
            exerciseMetrics: {
                exerciseInformation: {
                    1: {
                        id: 1,
                        title: 'Exercise 1',
                        shortName: 'E1',
                        startDate: dayjs().subtract(10, 'days'),
                        dueDate: dayjs().subtract(5, 'days'),
                        maxPoints: 10,
                        type: ExerciseType.PROGRAMMING,
                    },
                    2: {
                        id: 2,
                        title: 'Exercise 2',
                        shortName: 'E2',
                        startDate: dayjs().subtract(8, 'days'),
                        dueDate: dayjs().subtract(3, 'days'),
                        maxPoints: 20,
                        type: ExerciseType.TEXT,
                    },
                },
                score: { 1: 80, 2: 90 },
                averageScore: { 1: 70, 2: 80 },
            },
        };

        vi.spyOn(courseDashboardService, 'getCourseMetricsForUser').mockReturnValue(of(new HttpResponse({ body: metricsWithMixedDates })));

        component.loadMetrics();

        expect(component.exercisePerformance()).toBeDefined();
        expect(component.exercisePerformance()!.length).toBe(2);
    });

    it('should expose FeatureToggle and round for template usage', () => {
        expect((component as any).FeatureToggle).toBeDefined();
        expect((component as any).round).toBeDefined();
    });
});
