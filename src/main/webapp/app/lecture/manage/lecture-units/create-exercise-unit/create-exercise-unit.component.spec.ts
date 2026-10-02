import { type MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Observable, Subject, defer, finalize, of } from 'rxjs';
import { CreateExerciseUnitComponent } from 'app/lecture/manage/lecture-units/create-exercise-unit/create-exercise-unit.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { SortService } from 'app/foundation/service/sort.service';
import { ExerciseUnitService } from 'app/lecture/manage/lecture-units/services/exercise-unit.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Course } from 'app/course/shared/entities/course.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { UMLDiagramType } from '@tumaet/apollon';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CreateExerciseUnitComponent', () => {
    let createExerciseUnitComponentFixture: ComponentFixture<CreateExerciseUnitComponent>;
    let createExerciseUnitComponent: CreateExerciseUnitComponent;

    let courseManagementService: CourseManagementService;
    let findWithExercisesStub: MockInstance<CourseManagementService['findWithExercises']>;

    let exerciseUnitService: ExerciseUnitService;
    let findAllByLectureIdStub: MockInstance<ExerciseUnitService['findAllByLectureId']>;
    let createStub: MockInstance<ExerciseUnitService['create']>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FaIconComponent, CreateExerciseUnitComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                MockProvider(SortService),
                MockProvider(ExerciseUnitService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            parent: {
                                paramMap: of({
                                    get: (key: string) => {
                                        switch (key) {
                                            case 'lectureId':
                                                return 1;
                                        }
                                        return null;
                                    },
                                }),
                                parent: {
                                    paramMap: of({
                                        get: (key: string) => {
                                            switch (key) {
                                                case 'courseId':
                                                    return 1;
                                            }
                                            return null;
                                        },
                                    }),
                                },
                            },
                        },
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        createExerciseUnitComponentFixture = TestBed.createComponent(CreateExerciseUnitComponent);
        createExerciseUnitComponent = createExerciseUnitComponentFixture.componentInstance;
        courseManagementService = TestBed.inject(CourseManagementService);
        findWithExercisesStub = vi.spyOn(courseManagementService, 'findWithExercises');
        exerciseUnitService = TestBed.inject(ExerciseUnitService);
        findAllByLectureIdStub = vi.spyOn(exerciseUnitService, 'findAllByLectureId');
        createStub = vi.spyOn(exerciseUnitService, 'create');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        createExerciseUnitComponentFixture.detectChanges();
        expect(createExerciseUnitComponent).not.toBeNull();
    });

    it('should send POST requests for selected course exercises', async () => {
        const course = new Course();
        const textExercise = new TextExercise(course, undefined);
        textExercise.id = 1;
        const programmingExercise = new ProgrammingExercise(course, undefined);
        programmingExercise.id = 2;
        const quizExercise = new QuizExercise(course, undefined);
        quizExercise.id = 3;
        const fileUploadExercise = new FileUploadExercise(course, undefined);
        fileUploadExercise.id = 4;
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        modelingExercise.id = 5;
        course.exercises = [textExercise, programmingExercise, quizExercise, fileUploadExercise, modelingExercise];

        findWithExercisesStub.mockReturnValue(
            of(
                new HttpResponse({
                    body: course,
                    status: 200,
                }),
            ),
        );

        findAllByLectureIdStub.mockReturnValue(
            of(
                new HttpResponse({
                    body: [],
                    status: 200,
                }),
            ),
        );

        createStub.mockReturnValue(
            of(
                new HttpResponse({
                    body: new ExerciseUnit(),
                    status: 201,
                }),
            ),
        );

        createExerciseUnitComponentFixture.detectChanges();
        expect(createExerciseUnitComponent.exercisesAvailableForUnitCreation()).toHaveLength(5);

        const tableRows = createExerciseUnitComponentFixture.debugElement.queryAll(By.css('tbody > tr'));
        expect(tableRows).toHaveLength(5);
        tableRows[0].nativeElement.click(); // textExercise
        tableRows[1].nativeElement.click(); // programmingExercise
        tableRows[2].nativeElement.click(); // quizExercise
        expect(createExerciseUnitComponent.exercisesToCreateUnitFor()).toHaveLength(3);
        expect(createExerciseUnitComponent.exercisesAvailableForUnitCreation()[0].id).toEqual(textExercise.id);
        expect(createExerciseUnitComponent.exercisesAvailableForUnitCreation()[1].id).toEqual(programmingExercise.id);
        expect(createExerciseUnitComponent.exercisesAvailableForUnitCreation()[2].id).toEqual(quizExercise.id);

        const createButton = createExerciseUnitComponentFixture.debugElement.nativeElement.querySelector('#createButton');

        createExerciseUnitComponentFixture.detectChanges();
        createButton.click();

        await createExerciseUnitComponentFixture.whenStable();
        expect(createStub).toHaveBeenCalledTimes(3);
    });
    it('should let the lecture editor follow the create requests until they complete', () => {
        const course = new Course();
        const exercise = new TextExercise(course, undefined);
        exercise.id = 1;
        const response = new Subject<HttpResponse<ExerciseUnit>>();
        createStub.mockReturnValue(response);
        const createdSpy = vi.fn();
        let running = false;
        const trackRequest = vi.fn(<T>(request: Observable<T>) =>
            defer(() => {
                running = true;
                return request.pipe(finalize(() => (running = false)));
            }),
        );
        createExerciseUnitComponent.onExerciseUnitCreated.subscribe(createdSpy);
        createExerciseUnitComponentFixture.componentRef.setInput('shouldNavigateOnSubmit', false);
        createExerciseUnitComponentFixture.componentRef.setInput('lectureId', 1);
        createExerciseUnitComponentFixture.componentRef.setInput('trackRequest', trackRequest);
        createExerciseUnitComponent.exercisesToCreateUnitFor.set([exercise]);

        createExerciseUnitComponent.createExerciseUnits();
        expect(trackRequest).toHaveBeenCalledOnce();
        expect(createStub).toHaveBeenCalledOnce();
        expect(running).toBe(true);

        response.next(new HttpResponse({ body: new ExerciseUnit(), status: 201 }));
        response.complete();
        expect(running).toBe(false);
        expect(createdSpy).toHaveBeenCalledOnce();
    });

    it('should not report the created items once the form is closed, since the page that followed the requests reloads its list', () => {
        const exercise = new TextExercise(new Course(), undefined);
        exercise.id = 1;
        const response = new Subject<HttpResponse<ExerciseUnit>>();
        createStub.mockReturnValue(response);
        const createdSpy = vi.fn();
        createExerciseUnitComponent.onExerciseUnitCreated.subscribe(createdSpy);
        createExerciseUnitComponentFixture.componentRef.setInput('shouldNavigateOnSubmit', false);
        createExerciseUnitComponentFixture.componentRef.setInput('lectureId', 1);
        createExerciseUnitComponent.exercisesToCreateUnitFor.set([exercise]);

        createExerciseUnitComponent.createExerciseUnits();
        createExerciseUnitComponentFixture.destroy();
        response.next(new HttpResponse({ body: new ExerciseUnit(), status: 201 }));
        response.complete();

        expect(createdSpy).not.toHaveBeenCalled();
    });

    describe('choosing exercises', () => {
        let textExercise: TextExercise;
        let modelingExercise: ModelingExercise;

        function loadCourse(exercises: TextExercise[] | ModelingExercise[] | (TextExercise | ModelingExercise)[], linkedExerciseIds: number[] = []) {
            const course = new Course();
            course.exercises = exercises;
            findWithExercisesStub.mockReturnValue(of(new HttpResponse({ body: course, status: 200 })));
            const linkedUnits = linkedExerciseIds.map((id) => {
                const unit = new ExerciseUnit();
                unit.exercise = exercises.find((exercise) => exercise.id === id);
                return unit;
            });
            findAllByLectureIdStub.mockReturnValue(of(new HttpResponse({ body: linkedUnits, status: 200 })));
            createExerciseUnitComponentFixture.detectChanges();
        }

        function emptyRowKey(): string | null {
            const cell = createExerciseUnitComponentFixture.debugElement.query(By.css('tbody td[colspan]'));
            return cell ? (cell.nativeElement.getAttribute('ng-reflect-jhi-translate') ?? cell.nativeElement.textContent.trim()) : null;
        }

        beforeEach(() => {
            const course = new Course();
            textExercise = new TextExercise(course, undefined);
            textExercise.id = 1;
            textExercise.title = 'Requirements';
            modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            modelingExercise.id = 2;
            modelingExercise.title = 'Class diagram';
        });

        it('should offer only the exercises that are not content of the lecture yet', () => {
            loadCourse([textExercise, modelingExercise], [2]);

            expect(createExerciseUnitComponent.exercisesAvailableForUnitCreation()).toEqual([textExercise]);
            expect(createExerciseUnitComponentFixture.debugElement.queryAll(By.css('tbody tr[id^="exercise-"]'))).toHaveLength(1);
        });

        it('should toggle an exercise through its row and its checkbox alike', () => {
            loadCourse([textExercise, modelingExercise]);
            const row = createExerciseUnitComponentFixture.debugElement.query(By.css('#exercise-1'));

            row.nativeElement.click();
            expect(createExerciseUnitComponent.exercisesToCreateUnitFor()).toEqual([textExercise]);

            createExerciseUnitComponentFixture.detectChanges();
            const checkbox = row.query(By.css('input[type="checkbox"]'));
            checkbox.nativeElement.click();
            expect(createExerciseUnitComponent.exercisesToCreateUnitFor()).toEqual([]);
        });

        it.each([
            [1, true],
            [-1, false],
        ])('should sort by the column the user chose in the direction it shows (order %s)', (order, ascending) => {
            const sortService = TestBed.inject(SortService);
            const sortSpy = vi.spyOn(sortService, 'sortByProperty');
            loadCourse([textExercise, modelingExercise]);

            createExerciseUnitComponent.onSortChange({ field: 'title', order });

            expect(createExerciseUnitComponent.predicate()).toBe('title');
            expect(createExerciseUnitComponent.ascending()).toBe(ascending);
            expect(sortSpy).toHaveBeenLastCalledWith(expect.any(Array), 'title', ascending);
        });

        it.each([
            ['the course has no exercises', [], [], false],
            ['every exercise is content already', ['text'], [1], true],
        ])('should say so when %s', (_, exerciseKinds, linkedIds, hasCourseExercises) => {
            loadCourse(exerciseKinds.length ? [textExercise] : [], linkedIds as number[]);

            expect(createExerciseUnitComponent.hasCourseExercises()).toBe(hasCourseExercises);
            expect(emptyRowKey()).not.toBeNull();
        });
    });
});
