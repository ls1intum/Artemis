import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { CreateExerciseUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-exercise-unit/create-exercise-unit.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/exerciseUnit.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { AlertService } from 'app/core/util/alert.service';
import { UMLDiagramType } from '@ls1intum/apollon';

describe('CreateExerciseUnitComponent', () => {
    let createExerciseUnitComponentFixture: ComponentFixture<CreateExerciseUnitComponent>;
    let createExerciseUnitComponent: CreateExerciseUnitComponent;

    let courseManagementService;
    let findWithExercisesStub: jest.SpyInstance;

    let exerciseUnitService;
    let findAllByLectureIdStub: jest.SpyInstance;
    let createStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                CreateExerciseUnitComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockComponent(FaIconComponent),
            ],
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
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                createExerciseUnitComponentFixture = TestBed.createComponent(CreateExerciseUnitComponent);
                createExerciseUnitComponent = createExerciseUnitComponentFixture.componentInstance;
                courseManagementService = TestBed.inject(CourseManagementService);
                findWithExercisesStub = jest.spyOn(courseManagementService, 'findWithExercises');
                exerciseUnitService = TestBed.inject(ExerciseUnitService);
                findAllByLectureIdStub = jest.spyOn(exerciseUnitService, 'findAllByLectureId');
                createStub = jest.spyOn(exerciseUnitService, 'create');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        createExerciseUnitComponentFixture.detectChanges();
        expect(createExerciseUnitComponent).not.toBeNull();
    });

    it('should send POST requests for selected course exercises', fakeAsync(() => {
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
        expect(createExerciseUnitComponent.exercisesAvailableForUnitCreation).toHaveLength(5);

        const tableRows = createExerciseUnitComponentFixture.debugElement.queryAll(By.css('tbody > tr'));
        expect(tableRows).toHaveLength(5);
        tableRows[0].nativeElement.click(); // textExercise
        tableRows[1].nativeElement.click(); // programmingExercise
        tableRows[2].nativeElement.click(); // quizExercise
        expect(createExerciseUnitComponent.exercisesToCreateUnitFor).toHaveLength(3);
        expect(createExerciseUnitComponent.exercisesAvailableForUnitCreation[0].id).toEqual(textExercise.id);
        expect(createExerciseUnitComponent.exercisesAvailableForUnitCreation[1].id).toEqual(programmingExercise.id);
        expect(createExerciseUnitComponent.exercisesAvailableForUnitCreation[2].id).toEqual(quizExercise.id);

        const createButton = createExerciseUnitComponentFixture.debugElement.nativeElement.querySelector('#createButton');

        createExerciseUnitComponentFixture.detectChanges();
        createButton.click();

        createExerciseUnitComponentFixture.whenStable().then(() => {
            expect(createStub).toHaveBeenCalledTimes(3);
        });
    }));
});
