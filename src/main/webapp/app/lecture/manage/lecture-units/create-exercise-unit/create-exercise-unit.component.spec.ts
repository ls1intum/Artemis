import { type MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { CreateExerciseUnitComponent } from 'app/lecture/manage/lecture-units/create-exercise-unit/create-exercise-unit.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseUnitService } from 'app/lecture/manage/lecture-units/services/exercise-unit.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { UMLDiagramType } from '@tumaet/apollon';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CreateExerciseUnitComponent', () => {
    setupTestBed({ zoneless: true });

    let createExerciseUnitComponentFixture: ComponentFixture<CreateExerciseUnitComponent>;
    let createExerciseUnitComponent: CreateExerciseUnitComponent;

    let courseManagementService: CourseManagementService;
    let findWithExercisesStub: MockInstance<CourseManagementService['findWithExercises']>;

    let exerciseUnitService: ExerciseUnitService;
    let findAllByLectureIdStub: MockInstance<ExerciseUnitService['findAllByLectureId']>;
    let createStub: MockInstance<ExerciseUnitService['create']>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FaIconComponent, CreateExerciseUnitComponent, MockPipe(ArtemisTranslatePipe), MockDirective(SortDirective), MockDirective(SortByDirective)],
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
                                    },
                                }),
                                parent: {
                                    paramMap: of({
                                        get: (key: string) => {
                                            switch (key) {
                                                case 'courseId':
                                                    return 1;
                                            }
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
});
