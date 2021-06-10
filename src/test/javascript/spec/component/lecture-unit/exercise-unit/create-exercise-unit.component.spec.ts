import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { JhiAlertService, JhiSortByDirective, JhiSortDirective } from 'ng-jhipster';
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
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('CreateExerciseUnitComponent', () => {
    let createExerciseUnitComponentFixture: ComponentFixture<CreateExerciseUnitComponent>;
    let createExerciseUnitComponent: CreateExerciseUnitComponent;
    const sandbox = sinon.createSandbox();

    let courseManagementService;
    let findWithExercisesStub: sinon.SinonStub;

    let exerciseUnitService;
    let findAllByLectureIdStub: sinon.SinonStub;
    let createStub: sinon.SinonStub;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                CreateExerciseUnitComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(JhiSortDirective),
                MockDirective(JhiSortByDirective),
                MockComponent(FaIconComponent),
            ],
            providers: [
                MockProvider(CourseManagementService),
                MockProvider(JhiAlertService),
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
                findWithExercisesStub = sandbox.stub(courseManagementService, 'findWithExercises');
                exerciseUnitService = TestBed.inject(ExerciseUnitService);
                findAllByLectureIdStub = sandbox.stub(exerciseUnitService, 'findAllByLectureId');
                createStub = sandbox.stub(exerciseUnitService, 'create');
            });
    });

    afterEach(function () {
        sandbox.restore();
    });

    it('should initialize', () => {
        createExerciseUnitComponentFixture.detectChanges();
        expect(createExerciseUnitComponent).to.be.ok;
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

        findWithExercisesStub.returns(
            of(
                new HttpResponse({
                    body: course,
                    status: 200,
                }),
            ),
        );

        findAllByLectureIdStub.returns(
            of(
                new HttpResponse({
                    body: [],
                    status: 200,
                }),
            ),
        );

        createStub.returns(
            of(
                new HttpResponse({
                    body: new ExerciseUnit(),
                    status: 201,
                }),
            ),
        );

        createExerciseUnitComponentFixture.detectChanges();
        expect(createExerciseUnitComponent.exercisesAvailableForUnitCreation.length).to.equal(5);

        const tableRows = createExerciseUnitComponentFixture.debugElement.queryAll(By.css('tbody > tr'));
        expect(tableRows.length).to.equal(5);
        tableRows[0].nativeElement.click(); // textExercise
        tableRows[1].nativeElement.click(); // programmingExercise
        tableRows[2].nativeElement.click(); // quizExercise
        expect(createExerciseUnitComponent.exercisesToCreateUnitFor.length).to.equal(3);
        expect(createExerciseUnitComponent.exercisesAvailableForUnitCreation[0].id).to.equal(textExercise.id);
        expect(createExerciseUnitComponent.exercisesAvailableForUnitCreation[1].id).to.equal(programmingExercise.id);
        expect(createExerciseUnitComponent.exercisesAvailableForUnitCreation[2].id).to.equal(quizExercise.id);

        const createButton = createExerciseUnitComponentFixture.debugElement.nativeElement.querySelector('#createButton');

        createExerciseUnitComponentFixture.detectChanges();
        createButton.click();

        createExerciseUnitComponentFixture.whenStable().then(() => {
            expect(createStub).to.have.been.calledThrice;
        });
    }));
});
