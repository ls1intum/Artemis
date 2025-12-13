/**
 * Vitest tests for FileUploadExerciseDetailComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Params, provideRouter } from '@angular/router';
import { BehaviorSubject, Subscription, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import 'app/shared/util/array.extension';

import { FileUploadExerciseDetailComponent } from './file-upload-exercise-detail.component';
import { FileUploadExerciseService } from '../services/file-upload-exercise.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { AlertService } from 'app/shared/service/alert.service';
import { EventManager } from 'app/shared/service/event-manager.service';

import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { DetailOverviewListComponent } from 'app/shared/detail-overview-list/detail-overview-list.component';

describe('FileUploadExerciseDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let component: FileUploadExerciseDetailComponent;
    let fixture: ComponentFixture<FileUploadExerciseDetailComponent>;
    let fileUploadExerciseService: FileUploadExerciseService;
    let statisticsService: StatisticsService;
    let alertService: AlertService;

    let routeParams$: BehaviorSubject<Params>;

    const createCourse = (id = 123): Course => {
        const course = new Course();
        course.id = id;
        return course;
    };

    const createExercise = (course?: Course, exerciseGroup?: ExerciseGroup): FileUploadExercise => {
        const exercise = new FileUploadExercise(course, exerciseGroup);
        exercise.id = 456;
        exercise.title = 'Test Exercise';
        exercise.isAtLeastTutor = true;
        exercise.isAtLeastEditor = true;
        exercise.isAtLeastInstructor = true;
        exercise.problemStatement = '# Problem Statement';
        exercise.exampleSolution = '# Example Solution';
        exercise.gradingInstructions = '# Grading Instructions';
        return exercise;
    };

    const createStatistics = (): ExerciseManagementStatisticsDto => ({
        averageScoreOfExercise: 50,
        maxPointsOfExercise: 10,
        absoluteAveragePoints: 5,
        scoreDistribution: [5, 0, 0, 0, 0, 0, 0, 0, 0, 5],
        numberOfExerciseScores: 10,
        numberOfParticipations: 10,
        numberOfStudentsOrTeamsInCourse: 10,
        participationsInPercent: 100,
        numberOfPosts: 4,
        numberOfResolvedPosts: 2,
        resolvedPostsInPercent: 50,
    });

    beforeEach(async () => {
        routeParams$ = new BehaviorSubject({ exerciseId: 456 });

        await TestBed.configureTestingModule({
            imports: [FileUploadExerciseDetailComponent, TranslateModule.forRoot()],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: routeParams$.asObservable(),
                    },
                },
                {
                    provide: StatisticsService,
                    useValue: {
                        getExerciseStatistics: vi.fn().mockReturnValue(of(createStatistics())),
                    },
                },
                {
                    provide: EventManager,
                    useValue: {
                        subscribe: vi.fn().mockReturnValue({ id: 1 }),
                        destroy: vi.fn(),
                    },
                },
            ],
        })
            .overrideComponent(FileUploadExerciseDetailComponent, {
                remove: {
                    imports: [NonProgrammingExerciseDetailCommonActionsComponent, ExerciseDetailStatisticsComponent, DocumentationButtonComponent, DetailOverviewListComponent],
                },
                add: {
                    imports: [
                        MockComponent(NonProgrammingExerciseDetailCommonActionsComponent),
                        MockComponent(ExerciseDetailStatisticsComponent),
                        MockComponent(DocumentationButtonComponent),
                        MockComponent(DetailOverviewListComponent),
                    ],
                },
            })
            .compileComponents();

        fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);
        statisticsService = TestBed.inject(StatisticsService);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.clearAllMocks();
        if (fixture) {
            fixture.destroy();
        }
    });

    describe('initialization with course exercise', () => {
        it('should load exercise on init', async () => {
            const course = createCourse();
            const exercise = createExercise(course);
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.fileUploadExercise()).toEqual(exercise);
        });

        it('should not be in exam mode for course exercise', async () => {
            const course = createCourse();
            const exercise = createExercise(course);
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isExamExercise()).toBe(false);
        });

        it('should load statistics on init', async () => {
            const course = createCourse();
            const exercise = createExercise(course);
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(statisticsService.getExerciseStatistics).toHaveBeenCalledWith(456);
            expect(component.doughnutStats()).toBeDefined();
        });

        it('should compute course from exercise', async () => {
            const course = createCourse(789);
            const exercise = createExercise(course);
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.course()?.id).toBe(789);
        });

        it('should generate exercise detail sections', async () => {
            const course = createCourse();
            const exercise = createExercise(course);
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            const sections = component.exerciseDetailSections();
            expect(sections.length).toBeGreaterThan(0);
        });
    });

    describe('initialization with exam exercise', () => {
        it('should be in exam mode for exam exercise', async () => {
            const exam = new Exam();
            exam.id = 1;
            exam.course = createCourse();
            const exerciseGroup = new ExerciseGroup();
            exerciseGroup.id = 1;
            exerciseGroup.exam = exam;
            const exercise = createExercise(undefined, exerciseGroup);
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isExamExercise()).toBe(true);
        });

        it('should compute course from exam for exam exercise', async () => {
            const course = createCourse(999);
            const exam = new Exam();
            exam.id = 1;
            exam.course = course;
            const exerciseGroup = new ExerciseGroup();
            exerciseGroup.id = 1;
            exerciseGroup.exam = exam;
            const exercise = createExercise(undefined, exerciseGroup);
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.course()?.id).toBe(999);
        });
    });

    describe('error handling', () => {
        it('should handle exercise loading error', async () => {
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            const alertSpy = vi.spyOn(alertService, 'error');

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(alertSpy).toHaveBeenCalled();
            expect(component.fileUploadExercise()).toBeUndefined();
        });

        it('should handle statistics loading error gracefully', async () => {
            const course = createCourse();
            const exercise = createExercise(course);
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));
            vi.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.doughnutStats()).toBeUndefined();
        });
    });

    describe('reload functionality', () => {
        it('should reload exercise when event is triggered', async () => {
            const course = createCourse();
            const exercise = createExercise(course);
            const findSpy = vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));
            const eventManager = TestBed.inject(EventManager);
            let subscribedCallback: (() => void) | undefined;
            vi.spyOn(eventManager, 'subscribe').mockImplementation((name: string, callback: () => void) => {
                subscribedCallback = callback;
                return new Subscription();
            });

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(findSpy).toHaveBeenCalled();
            expect(subscribedCallback).toBeDefined();
        });
    });

    describe('formatted content', () => {
        it('should format problem statement as safe HTML', async () => {
            const course = createCourse();
            const exercise = createExercise(course);
            exercise.problemStatement = '# Test Problem';
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.formattedProblemStatement()).toBeDefined();
        });

        it('should format example solution as safe HTML', async () => {
            const course = createCourse();
            const exercise = createExercise(course);
            exercise.exampleSolution = '# Test Solution';
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.formattedExampleSolution()).toBeDefined();
        });

        it('should format grading instructions as safe HTML', async () => {
            const course = createCourse();
            const exercise = createExercise(course);
            exercise.gradingInstructions = '# Test Instructions';
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.formattedGradingInstructions()).toBeDefined();
        });
    });

    describe('exercise ID extraction', () => {
        it('should react to route param changes', async () => {
            const course = createCourse();
            const exercise1 = createExercise(course);
            exercise1.id = 1;
            const exercise2 = createExercise(course);
            exercise2.id = 2;

            const findSpy = vi
                .spyOn(fileUploadExerciseService, 'find')
                .mockReturnValueOnce(of(new HttpResponse({ body: exercise1 })))
                .mockReturnValueOnce(of(new HttpResponse({ body: exercise2 })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            routeParams$.next({ exerciseId: 2 });
            fixture.detectChanges();
            await fixture.whenStable();

            expect(findSpy).toHaveBeenCalledWith(456);
        });
    });

    describe('course signal', () => {
        it('should return undefined when exercise is undefined', async () => {
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.course()).toBeUndefined();
        });
    });

    describe('exercise detail sections', () => {
        it('should return empty array when exercise is undefined', async () => {
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.exerciseDetailSections()).toEqual([]);
        });

        it('should include grading section in details', async () => {
            const course = createCourse();
            const exercise = createExercise(course);
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            const sections = component.exerciseDetailSections();
            const gradingSection = sections.find((s) => s.headline === 'artemisApp.exercise.sections.grading');
            expect(gradingSection).toBeDefined();
        });
    });
});
