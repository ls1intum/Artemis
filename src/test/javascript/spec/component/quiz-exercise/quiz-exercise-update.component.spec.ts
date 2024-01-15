import { HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef, SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { NgbDate, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise } from 'app/entities/exercise.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { QuizBatch, QuizExercise, QuizMode, QuizStatus } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { QuizExerciseUpdateComponent } from 'app/exercises/quiz/manage/quiz-exercise-update.component';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { advanceTo } from 'jest-date-mock';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/core/util/alert.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { Exam } from 'app/entities/exam.model';
import { MockProvider } from 'ng-mocks';
import { Duration } from 'app/exercises/quiz/manage/quiz-exercise-interfaces';
import { QuizQuestionListEditComponent } from 'app/exercises/quiz/manage/quiz-question-list-edit.component';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';

describe('QuizExercise Update Detail Component', () => {
    let comp: QuizExerciseUpdateComponent;
    let exerciseGroupService: ExerciseGroupService;
    let courseManagementService: CourseManagementService;
    let quizExerciseService: QuizExerciseService;
    let exerciseService: ExerciseService;
    let fixture: ComponentFixture<QuizExerciseUpdateComponent>;
    let router: Router;
    let alertService: AlertService;
    let dragAndDropQuestionUtil: DragAndDropQuestionUtil;
    let shortAnswerQuestionUtil: ShortAnswerQuestionUtil;

    const course: Course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    const quizBatch = new QuizBatch();
    const mcQuestion = new MultipleChoiceQuestion();
    const answerOption = new AnswerOption();
    const exam = new Exam();
    exam.id = 1;

    const resetQuizExercise = () => {
        quizExercise.id = 456;
        quizExercise.title = 'test';
        quizExercise.duration = 600;
        answerOption.isCorrect = true;
        mcQuestion.title = 'test';
        mcQuestion.points = 10;
        mcQuestion.answerOptions = [answerOption];
        quizExercise.quizQuestions = [mcQuestion];
        quizExercise.quizBatches = [];
        quizExercise.releaseDate = undefined;
        quizExercise.dueDate = undefined;
        quizExercise.quizMode = QuizMode.SYNCHRONIZED;
        quizExercise.categories = [];
    };

    resetQuizExercise();

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: quizExercise.id }) }, queryParams: of({}) } as any as ActivatedRoute;

    const createValidMCQuestion = () => {
        const question = new MultipleChoiceQuestion();
        question.title = 'test';
        const answerOption1 = new AnswerOption();
        answerOption1.text = 'right answer';
        answerOption1.explanation = 'right explanation';
        answerOption1.isCorrect = true;
        const answerOption2 = new AnswerOption();
        answerOption2.text = 'wrong answer';
        answerOption2.explanation = 'wrong explanation';
        answerOption2.hint = 'wrong hint';
        answerOption2.isCorrect = false;
        question.answerOptions = [answerOption1, answerOption2];
        question.points = 10;
        return { question, answerOption1, answerOption2 };
    };

    const createValidDnDQuestion = () => {
        const question = new DragAndDropQuestion();
        question.title = 'test';
        const dragItem1 = new DragItem();
        dragItem1.text = 'dragItem 1';
        dragItem1.pictureFilePath = 'test';
        const dragItem2 = new DragItem();
        dragItem2.text = 'dragItem 1';
        question.dragItems = [dragItem1, dragItem2];
        const dropLocation = new DropLocation();
        dropLocation.posX = 50;
        dropLocation.posY = 60;
        dropLocation.width = 70;
        dropLocation.height = 80;
        question.dropLocations = [dropLocation];
        const correctDragAndDropMapping = new DragAndDropMapping(dragItem1, dropLocation);
        question.correctMappings = [correctDragAndDropMapping];
        question.points = 10;
        return { question, dragItem1, dragItem2, dropLocation, correctDragAndDropMapping };
    };

    const createValidSAQuestion = () => {
        const question = new ShortAnswerQuestion();
        question.title = 'test';
        const shortAnswerSolution1 = new ShortAnswerSolution();
        shortAnswerSolution1.text = 'solution 1';
        const shortAnswerSolution2 = new ShortAnswerSolution();
        shortAnswerSolution2.text = 'solution 2';
        question.solutions = [shortAnswerSolution1, shortAnswerSolution2];
        const spot1 = new ShortAnswerSpot();
        spot1.question = question;
        spot1.spotNr = 1;
        spot1.width = 50;
        const spot2 = new ShortAnswerSpot();
        spot2.question = question;
        spot2.spotNr = 2;
        spot2.width = 70;
        question.spots = [spot1, spot2];
        const shortAnswerMapping1 = new ShortAnswerMapping(spot1, shortAnswerSolution1);
        const shortAnswerMapping2 = new ShortAnswerMapping(spot2, shortAnswerSolution2);
        question.correctMappings = [shortAnswerMapping1, shortAnswerMapping2];
        question.points = 10;
        return { question, shortAnswerMapping1, shortAnswerMapping2, spot1, spot2, shortAnswerSolution1, shortAnswerSolution2 };
    };

    const configureTestBed = (testRoute?: ActivatedRoute) => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [QuizExerciseUpdateComponent],
            providers: [
                MockProvider(NgbModal),
                MockProvider(ChangeDetectorRef),
                MockProvider(DragAndDropQuestionUtil),
                MockProvider(ShortAnswerQuestionUtil),
                { provide: ActivatedRoute, useValue: testRoute || route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
            ],
        })
            .overrideTemplate(QuizExerciseUpdateComponent, '')
            .compileComponents();
    };

    const configureFixtureAndServices = () => {
        fixture = TestBed.createComponent(QuizExerciseUpdateComponent);
        comp = fixture.componentInstance;
        courseManagementService = fixture.debugElement.injector.get(CourseManagementService);
        quizExerciseService = fixture.debugElement.injector.get(QuizExerciseService);
        router = fixture.debugElement.injector.get(Router);
        alertService = fixture.debugElement.injector.get(AlertService);
        dragAndDropQuestionUtil = fixture.debugElement.injector.get(DragAndDropQuestionUtil);
        shortAnswerQuestionUtil = fixture.debugElement.injector.get(ShortAnswerQuestionUtil);
        exerciseGroupService = fixture.debugElement.injector.get(ExerciseGroupService);
        exerciseService = fixture.debugElement.injector.get(ExerciseService);
    };

    describe('onInit', () => {
        let quizExerciseServiceStub: jest.SpyInstance;
        let courseManagementServiceStub: jest.SpyInstance;
        let exerciseGroupServiceStub: jest.SpyInstance;
        let initStub: jest.SpyInstance;
        const configureStubs = () => {
            quizExerciseServiceStub = jest.spyOn(quizExerciseService, 'find');
            courseManagementServiceStub = jest.spyOn(courseManagementService, 'find');
            exerciseGroupServiceStub = jest.spyOn(exerciseGroupService, 'find');
            initStub = jest.spyOn(comp, 'init');
            quizExerciseServiceStub.mockReturnValue(of(new HttpResponse<QuizExercise>({ body: quizExercise })));
            courseManagementServiceStub.mockReturnValue(of(new HttpResponse<Course>({ body: course })));
            exerciseGroupServiceStub.mockReturnValue(of(new HttpResponse<ExerciseGroup>({ body: undefined })));
        };

        describe('without exam id', () => {
            beforeEach(waitForAsync(configureTestBed));
            beforeEach(configureFixtureAndServices);
            it('should call courseExerciseService.find and quizExerciseService.find', () => {
                // GIVEN
                configureStubs();
                // WHEN
                comp.course = course;
                comp.ngOnInit();

                // THEN
                expect(quizExerciseServiceStub).toHaveBeenCalledOnce();
                expect(courseManagementServiceStub).toHaveBeenCalledOnce();
                expect(exerciseGroupServiceStub).not.toHaveBeenCalled();
                expect(initStub).toHaveBeenCalledOnce();
            });

            afterEach(() => {
                jest.clearAllMocks();
            });
        });

        describe('with exam id', () => {
            const testRoute = {
                snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: quizExercise.id, examId: 1, exerciseGroupId: 2 }) },
                queryParams: of({}),
            } as any as ActivatedRoute;
            beforeEach(waitForAsync(() => configureTestBed(testRoute)));
            beforeEach(configureFixtureAndServices);
            it('should call exerciseGroupService.find', () => {
                configureStubs();
                comp.course = course;
                comp.ngOnInit();
                expect(quizExerciseServiceStub).toHaveBeenCalledOnce();
                expect(courseManagementServiceStub).toHaveBeenCalledOnce();
                expect(exerciseGroupServiceStub).toHaveBeenCalledOnce();
                expect(initStub).toHaveBeenCalledOnce();
            });

            afterEach(() => {
                jest.clearAllMocks();
            });
        });

        describe('with exam id but without exercise id', () => {
            const testRoute = { snapshot: { paramMap: convertToParamMap({ courseId: course.id, examId: 1, exerciseGroupId: 2 }) }, queryParams: of({}) } as any as ActivatedRoute;
            beforeEach(waitForAsync(() => configureTestBed(testRoute)));
            beforeEach(configureFixtureAndServices);
            it('should call exerciseGroupService.find', () => {
                configureStubs();
                comp.course = course;
                comp.ngOnInit();
                expect(quizExerciseServiceStub).not.toHaveBeenCalled();
                expect(courseManagementServiceStub).toHaveBeenCalledOnce();
                expect(exerciseGroupServiceStub).toHaveBeenCalledOnce();
                expect(initStub).toHaveBeenCalledOnce();
            });

            afterEach(() => {
                jest.clearAllMocks();
            });
        });

        describe('without exam id and exercise id', () => {
            const testRoute = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) }, queryParams: of({}) } as any as ActivatedRoute;
            beforeEach(waitForAsync(() => configureTestBed(testRoute)));
            beforeEach(configureFixtureAndServices);
            it('should call exerciseGroupService.find', () => {
                configureStubs();
                comp.course = course;
                comp.ngOnInit();
                expect(quizExerciseServiceStub).not.toHaveBeenCalled();
                expect(courseManagementServiceStub).toHaveBeenCalledOnce();
                expect(exerciseGroupServiceStub).not.toHaveBeenCalled();
                expect(initStub).toHaveBeenCalledOnce();
            });

            afterEach(() => {
                jest.clearAllMocks();
            });
        });

        describe('with exercise id and exam with test runs', () => {
            const testRoute = {
                snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: quizExercise.id, examId: 1, exerciseGroupId: 2 }) },
                queryParams: of({}),
            } as any as ActivatedRoute;
            beforeEach(waitForAsync(() => configureTestBed(testRoute)));
            beforeEach(configureFixtureAndServices);

            it('should not call alert service', () => {
                configureStubs();
                comp.course = course;
                comp.isImport = true;
                quizExercise.testRunParticipationsExist = true;

                const alertServiceStub = jest.spyOn(alertService, 'warning');
                comp.ngOnInit();

                expect(alertServiceStub).not.toHaveBeenCalled();
            });

            it('should call alert service', () => {
                configureStubs();
                comp.course = course;
                comp.isImport = false;
                quizExercise.testRunParticipationsExist = true;

                const alertServiceStub = jest.spyOn(alertService, 'warning');
                comp.ngOnInit();

                expect(alertServiceStub).toHaveBeenCalledOnce();
            });

            afterEach(() => {
                jest.clearAllMocks();
            });
        });

        describe('validate dates', () => {
            // setup
            beforeEach(() => {
                resetQuizExercise();
                comp.course = course;
                comp.init();
                comp.quizExercise = quizExercise;
                comp.duration = new Duration(5, 15);
                comp.scheduleQuizStart = true;
            });

            it.each([[QuizMode.SYNCHRONIZED], [QuizMode.BATCHED], [QuizMode.INDIVIDUAL]])('should set errors to false for valid dates for %s mode', (quizMode) => {
                comp.quizExercise.quizMode = quizMode;
                comp.cacheValidation();

                if (quizMode !== QuizMode.SYNCHRONIZED) {
                    comp.addQuizBatch();
                }

                const now = dayjs();
                comp.quizExercise.releaseDate = now;

                comp.validateDate();
                expect(comp.quizExercise.dueDateError).toBeFalsy();
                expect(comp.hasErrorInQuizBatches()).toBeFalse();

                comp.quizExercise!.quizBatches![0].startTime = now.add(1, 'days');

                comp.validateDate();
                expect(comp.quizExercise.dueDateError).toBeFalsy();
                expect(comp.hasErrorInQuizBatches()).toBeFalse();

                comp.quizExercise.dueDate = now.add(2, 'days');

                comp.validateDate();
                expect(comp.quizExercise.dueDateError).toBeFalsy();
                expect(comp.hasErrorInQuizBatches()).toBeFalse();
            });

            it.each([[QuizMode.SYNCHRONIZED], [QuizMode.BATCHED], [QuizMode.INDIVIDUAL]])('should set errors to true for invalid dates for %s mode', (quizMode) => {
                comp.quizExercise.quizMode = quizMode;
                comp.cacheValidation();

                if (quizMode !== QuizMode.SYNCHRONIZED) {
                    comp.addQuizBatch();
                }

                const now = dayjs();
                comp.quizExercise.releaseDate = now;

                comp.quizExercise!.quizBatches![0].startTime = now.add(-1, 'days');

                comp.validateDate();
                expect(comp.quizExercise.dueDateError).toBeFalsy();
                expect(comp.hasErrorInQuizBatches()).toBeTrue();

                comp.quizExercise.dueDate = now.add(-2, 'days');

                comp.validateDate();
                expect(comp.quizExercise.dueDateError).toBeTrue();
                expect(comp.hasErrorInQuizBatches()).toBeTrue();

                comp.quizExercise!.quizBatches![0].startTime = now.add(1, 'days');
                comp.quizExercise.dueDate = now.add(1, 'days');

                comp.validateDate();
                expect(comp.quizExercise.dueDateError).toBeFalsy();
                if (quizMode !== QuizMode.SYNCHRONIZED) {
                    // dueDate for SYNCHRONIZED quizzes are calculated so no need to validate.
                    expect(comp.hasErrorInQuizBatches()).toBeTrue();
                }
            });
        });

        describe('set isEditable', () => {
            const testRoute = {
                snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: quizExercise.id, examId: 1, exerciseGroupId: 2 }) },
                queryParams: of({}),
            } as any as ActivatedRoute;
            beforeEach(waitForAsync(() => configureTestBed(testRoute)));
            beforeEach(configureFixtureAndServices);
            beforeEach(() => {
                comp.quizExercise = new QuizExercise(undefined, undefined);
                comp.quizExercise.id = 1;
                comp.quizExercise.title = 'test';
                comp.quizExercise.duration = 600;
                const { question } = createValidMCQuestion();
                comp.quizExercise.quizQuestions = [question];
                comp.quizExercise.quizMode = QuizMode.SYNCHRONIZED;
                comp.quizExercise.status = QuizStatus.VISIBLE;
                comp.quizExercise.isAtLeastEditor = true;
                comp.quizExercise.quizEnded = false;
            });

            it('should set isEditable to true if new quiz', () => {
                comp.init();
                comp.quizExercise.id = undefined;
                expect(comp.quizExercise.isEditable).toBeTrue();
            });

            it('should set isEditable to true if existing quiz is synchronized, not active and not over', () => {
                comp.init();
                expect(comp.quizExercise.isEditable).toBeTrue();
            });

            it('should set isEditable to true if existing quiz is batched, no batch exists, not active and not over', () => {
                comp.quizExercise.quizMode = QuizMode.BATCHED;
                comp.quizExercise.quizBatches = undefined;
                comp.init();
                expect(comp.quizExercise.isEditable).toBeTrue();
            });

            it('should set isEditable to false if existing quiz is batched, batch exists, not active and not over', () => {
                comp.quizExercise.quizMode = QuizMode.BATCHED;
                comp.quizExercise.quizBatches = [new QuizBatch()];
                comp.init();
                expect(comp.quizExercise.isEditable).toBeFalse();
            });

            it('should set isEditable to false if existing quiz is synchronized, active, and not over', () => {
                comp.quizExercise.quizMode = QuizMode.SYNCHRONIZED;
                comp.quizExercise.status = QuizStatus.ACTIVE;
                comp.init();
                expect(comp.quizExercise.isEditable).toBeFalse();
            });

            it('should set isEditable to false if existing quiz is synchronized, not active, and over', () => {
                comp.quizExercise.quizMode = QuizMode.SYNCHRONIZED;
                comp.quizExercise.quizEnded = true;
                comp.init();
                expect(comp.quizExercise.isEditable).toBeFalse();
            });
        });

        it('should updateCategories properly by making category available for selection again when removing it', () => {
            comp.quizExercise = quizExercise;
            comp.exerciseCategories = [];
            const newCategories = [{ category: 'Easy' }, { category: 'Hard' }];

            comp.updateCategories(newCategories);

            expect(comp.quizExercise.categories).toEqual(newCategories);
            expect(comp.exerciseCategories).toEqual(newCategories);
        });
    });

    describe('without routeChange', () => {
        beforeEach(waitForAsync(configureTestBed));
        beforeEach(configureFixtureAndServices);

        describe('init', () => {
            let exerciseServiceCategoriesAsStringStub: jest.SpyInstance;
            let courseServiceStub: jest.SpyInstance;
            const testExistingCategories = [
                { exerciseId: 1, category: 'eCategory1', color: 'eColor1' },
                { exerciseId: 2, category: 'eCategory2', color: 'eColor2' },
            ];
            let prepareEntitySpy: jest.SpyInstance;
            let alertServiceStub: jest.SpyInstance;
            beforeEach(() => {
                comp.course = course;
                comp.courseId = course.id;
                courseServiceStub = jest.spyOn(courseManagementService, 'findAllCategoriesOfCourse');
                courseServiceStub.mockReturnValue(of(new HttpResponse<string[]>({ body: ['category1', 'category2'] })));
                exerciseServiceCategoriesAsStringStub = jest.spyOn(exerciseService, 'convertExerciseCategoriesAsStringFromServer');
                exerciseServiceCategoriesAsStringStub.mockReturnValue(testExistingCategories);
                prepareEntitySpy = jest.spyOn(comp, 'prepareEntity');
                alertServiceStub = jest.spyOn(alertService, 'error');
            });

            it('should set quizExercise to entity if quiz exercise not defined', () => {
                expect(comp.quizExercise).toBeUndefined();
                comp.init();
                expect(comp.quizExercise).toBeDefined();
                expect(prepareEntitySpy).toHaveBeenCalledWith(comp.quizExercise);
                expect(comp.savedEntity).toEqual(new QuizExercise(undefined, undefined));
                expect(comp.quizExercise.course).toEqual(course);
                expect(courseServiceStub).toHaveBeenCalledWith(course.id);
            });

            it('should set entity to quiz exercise if quiz exercise defined', () => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                comp.quizExercise.course = course;
                comp.init();
                expect(comp.savedEntity).toEqual(quizExercise);
            });

            it('should set quizExercise exercise group if exam and it does not have one', () => {
                comp.isExamMode = true;
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                expect(comp.quizExercise.exerciseGroup).toBeUndefined();
                comp.exerciseGroup = new ExerciseGroup();
                comp.init();
                expect(comp.quizExercise.exerciseGroup).toEqual(comp.exerciseGroup);
            });

            it('should call on error if course service fails', () => {
                courseServiceStub.mockReturnValue(throwError(() => ({ status: 404 })));
                comp.init();
                expect(alertServiceStub).toHaveBeenCalledOnce();
            });
        });

        describe('onDurationChange', () => {
            // setup
            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
            });

            it('should update duration and quizExercise.duration with same values', () => {
                comp.duration = { minutes: 15, seconds: 30 };
                comp.onDurationChange();

                // compare duration with quizExercise.duration
                const durationAsSeconds = dayjs.duration(comp.duration).asSeconds();
                expect(durationAsSeconds).toEqual(comp.quizExercise.duration);
            });

            it('should increase minutes when reaching 60 seconds', () => {
                comp.duration = { minutes: 0, seconds: 60 };
                comp.onDurationChange();

                expect(comp.duration.minutes).toBe(1);
                expect(comp.duration.seconds).toBe(0);
            });

            it('should decrease minutes when reaching -1 seconds', () => {
                comp.duration = { minutes: 1, seconds: -1 };
                comp.onDurationChange();

                expect(comp.duration.minutes).toBe(0);
                expect(comp.duration.seconds).toBe(59);
            });

            it('should set duration to due date release date difference', () => {
                comp.isExamMode = true;
                comp.quizExercise.releaseDate = dayjs();
                comp.quizExercise.dueDate = dayjs().add(1530, 's');
                comp.onDurationChange();
                expect(comp.quizExercise.duration).toBe(1530);
                comp.isExamMode = false;
            });
        });

        describe('ngOnChanges', () => {
            it('should call init if there are changes on course or quiz exercise', () => {
                const change = new SimpleChange(0, 1, false);
                const initStub = jest.spyOn(comp, 'init').mockImplementation();

                comp.ngOnChanges({ course: change });
                expect(initStub).toHaveBeenCalledOnce();
                initStub.mockClear();

                comp.ngOnChanges({ quizExercise: change });
                expect(initStub).toHaveBeenCalledOnce();
                initStub.mockClear();

                comp.ngOnChanges({ course: change, quizExercise: change });
                expect(initStub).toHaveBeenCalledOnce();
                initStub.mockClear();

                comp.ngOnChanges({});
                expect(initStub).not.toHaveBeenCalled();
            });
        });

        describe('updateCategories', () => {
            it('should update categories to given categories', () => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                const exerciseCategory1 = { exerciseId: 1, category: 'category1', color: 'color1' };
                const exerciseCategory2 = { exerciseId: 1, category: 'category1', color: 'color1' };
                const expected = [exerciseCategory1, exerciseCategory2];
                comp.updateCategories([exerciseCategory1, exerciseCategory2]);
                expect(comp.quizExercise.categories).toEqual(expected);
            });
        });

        describe('show dropdown', () => {
            const resetQuizExerciseAndSet = () => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                quizExercise.quizBatches = [quizBatch];
            };

            it('should return isVisibleBeforeStart if no quizExercise', () => {
                expect(comp.quizExercise).toBeUndefined();
                expect(comp.showDropdown).toBe('isVisibleBeforeStart');
            });

            it('should return isVisibleBeforeStart if quizExercise not planned to start', () => {
                resetQuizExerciseAndSet();
                quizExercise.quizBatches = [];
                expect(comp.showDropdown).toBe('isVisibleBeforeStart');
            });

            it('should return if end of exercise is in the past', () => {
                resetQuizExerciseAndSet();
                comp.quizExercise.quizStarted = true;
                comp.quizExercise.quizEnded = true;
                expect(comp.showDropdown).toBe('isOpenForPractice');
            });

            it('should return if end of exercise is in the future but release date is in the past', () => {
                resetQuizExerciseAndSet();
                comp.quizExercise.quizStarted = true;
                comp.quizExercise.quizEnded = false;
                expect(comp.showDropdown).toBe('active');
            });
        });

        describe('unloading notification and can deactivate', () => {
            it('should return opposite of pendingChangesCache', () => {
                comp.pendingChangesCache = true;
                expect(comp.canDeactivate()).toBeFalse();
                comp.pendingChangesCache = false;
                expect(comp.canDeactivate()).toBeTrue();
            });

            it('should set event return value to translate if not canDeactivate', () => {
                comp.pendingChangesCache = true;
                const ev = { returnValue: undefined };
                comp.unloadNotification(ev);
                expect(ev.returnValue).toBe('pendingChanges');
            });

            it('should not set event return value to translate if  canDeactivate', () => {
                comp.pendingChangesCache = false;
                const ev = { returnValue: undefined };
                comp.unloadNotification(ev);
                expect(ev.returnValue).toBeUndefined();
            });
        });

        describe('check if date is in the past', () => {
            let tomorrow: NgbDate;
            beforeEach(() => {
                tomorrow = new NgbDate(2020, 11, 16);
                // advanceTo 2020 11 15
                // dayjs adds one month
                advanceTo(new Date(2020, 10, 15, 0, 0, 0));
            });

            it('should return true if given month is before month we are in', () => {
                expect(comp.isDateInPast(tomorrow, { month: 10 })).toBeTrue();
            });

            it('should return false if given month is same or after month we are in', () => {
                expect(comp.isDateInPast(tomorrow, { month: 11 })).toBeFalse();
            });

            it('should return true if given date is before now', () => {
                const past = new NgbDate(2020, 11, 10);
                expect(comp.isDateInPast(past, { month: 11 })).toBeTrue();
            });

            it('should return false if given date is before now', () => {
                expect(comp.isDateInPast(tomorrow, { month: 11 })).toBeFalse();
            });
        });

        describe('calculating max exercise score', () => {
            it('should return sum of scores of the questions', () => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                const { question: multiQuestion } = createValidMCQuestion();
                multiQuestion.points = 1;
                comp.quizExercise.quizQuestions = [multiQuestion];
                expect(comp.calculateMaxExerciseScore()).toBe(1);
                const { question: dndQuestion } = createValidDnDQuestion();
                dndQuestion.points = 2;
                comp.quizExercise.quizQuestions = [multiQuestion, dndQuestion];
                expect(comp.calculateMaxExerciseScore()).toBe(3);
                const { question: saQuestion } = createValidSAQuestion();
                saQuestion.points = 3;
                comp.quizExercise.quizQuestions = [multiQuestion, dndQuestion, saQuestion];
                expect(comp.calculateMaxExerciseScore()).toBe(6);
            });
        });

        describe('quiz validity', () => {
            // setup
            const removeQuestionTitleAndExpectInvalidQuiz = (question: QuizQuestion) => {
                question.title = '';
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            };

            const removeCorrectMappingsAndExpectInvalidQuiz = (question: DragAndDropQuestion | ShortAnswerQuestion) => {
                question.correctMappings = [];
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            };

            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
            });

            it('should be valid with default test setting', () => {
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });

            it('should not be valid without a quiz title', () => {
                quizExercise.title = '';
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            });

            describe('unknown question type', () => {
                let question: MultipleChoiceQuestion;
                beforeEach(() => {
                    const multiChoiceQuestion = createValidMCQuestion();
                    question = multiChoiceQuestion.question;
                    question.type = undefined;
                    comp.quizExercise.quizQuestions = [question];
                });

                it('should be valid if a question has unknown type and a title', () => {
                    question.title = 'test';
                    comp.cacheValidation();
                    expect(comp.quizIsValid).toBeTrue();
                });

                it('should not be valid if a question has unknown type and no title', () => {
                    question.title = '';
                    comp.cacheValidation();
                    expect(comp.quizIsValid).toBeFalse();
                });
            });

            it('should not be valid if a question has negative score', () => {
                const { question } = createValidMCQuestion();
                question.points = -1;
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            });

            it('should be valid with valid MC question', () => {
                const { question } = createValidMCQuestion();
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });

            it('should not be valid if MC question has no title', () => {
                const { question } = createValidMCQuestion();
                removeQuestionTitleAndExpectInvalidQuiz(question);
            });

            it('should not be valid if MC question has no correct answer', () => {
                const { question } = createValidMCQuestion();
                question.answerOptions = [];
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            });

            it('should be valid if MC question hint is <= 255 characters', () => {
                const { question } = createValidMCQuestion();
                question.hint = 'This is an example hint';
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });

            it('should be valid if MC question explanation is <= 500 characters', () => {
                const { question } = createValidMCQuestion();
                question.explanation = 'This is an example explanation';
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });

            it('should be valid if MC question hint has exactly 255 characters', () => {
                const { question } = createValidMCQuestion();
                question.hint = 'f'.repeat(255);
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });

            it('should be valid if MC question explanation has exactly 500 characters', () => {
                const { question } = createValidMCQuestion();
                question.explanation = 'f'.repeat(500);
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });

            it('should not be valid if MC question hint has more than 255 characters', () => {
                const { question } = createValidMCQuestion();
                question.hint = 'f'.repeat(255 + 1);
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            });

            it('should not be valid if MC question explanation has more than 500 characters', () => {
                const { question } = createValidMCQuestion();
                question.explanation = 'f'.repeat(500 + 1);
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            });

            it('should be valid if MC answer option hint has exactly 255 characters', () => {
                const { question } = createValidMCQuestion();
                question.answerOptions![0]!.hint = 'f'.repeat(255);
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });

            it('should be valid if MC answer option explanation has exactly 500 characters', () => {
                const { question } = createValidMCQuestion();
                question.answerOptions![0]!.explanation = 'f'.repeat(500);
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });

            it('should not be valid if MC answer option hint has more than 255 characters', () => {
                const { question } = createValidMCQuestion();
                question.answerOptions![0]!.hint = 'f'.repeat(255 + 1);
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            });

            it('should not be valid if MC answer option explanation has more than 1000 characters', () => {
                const { question } = createValidMCQuestion();
                question.answerOptions![0]!.explanation = 'f'.repeat(500 + 1);
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            });

            it('should be valid if MC question has scoring type single choice', () => {
                const { question } = createValidMCQuestion();
                question.singleChoice = true;
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });

            it('should not be valid if MC single choice question has multiple correct answers', () => {
                const { question } = createValidMCQuestion();
                question.singleChoice = true;
                question.answerOptions![1]!.isCorrect = true;
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            });

            it('should be valid with valid DnD question', () => {
                const { question } = createValidDnDQuestion();
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });

            it('should not be valid if DnD question has no title', () => {
                const { question } = createValidDnDQuestion();
                removeQuestionTitleAndExpectInvalidQuiz(question);
            });

            it('should not be valid if DnD question has no correct mapping', () => {
                const { question } = createValidDnDQuestion();
                removeCorrectMappingsAndExpectInvalidQuiz(question);
            });

            it('should be valid if DnD question hint has exactly 255 characters', () => {
                const { question } = createValidDnDQuestion();
                question.hint = 'f'.repeat(255);
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });

            it('should be valid if DnD question explanation has exactly 500 characters', () => {
                const { question } = createValidDnDQuestion();
                question.explanation = 'f'.repeat(500);
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });

            it('should not be valid if DnD question hint has more than 255 characters', () => {
                const { question } = createValidDnDQuestion();
                question.hint = 'f'.repeat(255 + 1);
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            });

            it('should not be valid if DnD question explanation has more than 500 characters', () => {
                const { question } = createValidDnDQuestion();
                question.explanation = 'f'.repeat(500 + 1);
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            });

            it('should be valid with valid SA question', () => {
                const { question } = createValidSAQuestion();
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });

            it('should not be valid if SA question has no title', () => {
                const { question } = createValidSAQuestion();
                removeQuestionTitleAndExpectInvalidQuiz(question);
            });

            it('should not be valid with SA question with too long answer option', () => {
                const { question } = createValidSAQuestion();
                // @ts-ignore
                question.solutions[0].text = 'a'.repeat(250);
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            });

            it('should not be valid if SA question has no correct mapping', () => {
                const { question } = createValidSAQuestion();
                removeCorrectMappingsAndExpectInvalidQuiz(question);
            });

            it('should be valid for synchronized mode when dueDate is less than releaseDate', () => {
                const now = dayjs();
                comp.quizExercise.quizMode = QuizMode.SYNCHRONIZED;
                comp.scheduleQuizStart = true;
                comp.quizExercise.quizBatches = [new QuizBatch()];
                comp.quizExercise.releaseDate = now;
                comp.quizExercise.startDate = now.add(1, 'day');
                comp.quizExercise.dueDate = now.add(-1, 'day');
                comp.cacheValidation();
                expect(comp.quizExercise.dueDateError).toBeFalsy();
                expect(comp.quizExercise.dueDate).toBeUndefined();
            });

            it('should not be valid if question point is not in valid range', () => {
                let { question } = createValidMCQuestion();
                question.points = 10000;
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
                question = createValidDnDQuestion().question;
                question.points = 0;
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
                question = createValidSAQuestion().question;
                question.points = -1;
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeFalse();
            });

            it('should not be valid if question point is in valid range', () => {
                let { question } = createValidMCQuestion();
                question.points = 9999;
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
                question = createValidDnDQuestion().question;
                question.points = 100;
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
                question = createValidSAQuestion().question;
                question.points = 1;
                comp.quizExercise.quizQuestions = [question];
                comp.cacheValidation();
                expect(comp.quizIsValid).toBeTrue();
            });
        });

        it('should delete a question', () => {
            const mcQuestion = createValidMCQuestion().question;
            const dndQuestion = createValidDnDQuestion().question;
            comp.quizExercise = { ...quizExercise, quizQuestions: [mcQuestion, dndQuestion] } as QuizExercise;
            comp.deleteQuestion(dndQuestion);
            expect(comp.quizExercise.quizQuestions).toEqual([mcQuestion]);
        });

        describe('saving', () => {
            let quizExerciseServiceCreateStub: jest.SpyInstance;
            let quizExerciseServiceUpdateStub: jest.SpyInstance;
            let quizExerciseServiceImportStub: jest.SpyInstance;
            let exerciseSanitizeSpy: jest.SpyInstance;
            const saveQuizWithPendingChangesCache = () => {
                comp.cacheValidation();
                comp.pendingChangesCache = true;
                if (comp.courseId) {
                    comp.quizQuestionListEditComponent = new QuizQuestionListEditComponent(new MockNgbModalService() as any as NgbModal);
                    jest.spyOn(comp.quizQuestionListEditComponent, 'parseAllQuestions').mockImplementation();
                }
                comp.save();
            };

            const saveAndExpectAlertService = () => {
                console.error = jest.fn();
                const alertServiceStub = jest.spyOn(alertService, 'error');
                saveQuizWithPendingChangesCache();
                expect(alertServiceStub).toHaveBeenCalledOnce();
                expect(comp.isSaving).toBeFalse();
                expect(console.error).toHaveBeenCalledOnce();
            };

            beforeEach(() => {
                comp.course = course;
                comp.courseId = course.id!;
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                quizExerciseServiceCreateStub = jest.spyOn(quizExerciseService, 'create');
                quizExerciseServiceCreateStub.mockReturnValue(of(new HttpResponse<QuizExercise>({ body: quizExercise })));
                quizExerciseServiceUpdateStub = jest.spyOn(quizExerciseService, 'update');
                quizExerciseServiceUpdateStub.mockReturnValue(of(new HttpResponse<QuizExercise>({ body: quizExercise })));
                quizExerciseServiceImportStub = jest.spyOn(quizExerciseService, 'import');
                quizExerciseServiceImportStub.mockReturnValue(of(new HttpResponse<QuizExercise>({ body: quizExercise })));
                exerciseSanitizeSpy = jest.spyOn(Exercise, 'sanitize');
            });

            afterEach(() => {
                jest.clearAllMocks();
            });

            it('should call create if valid and quiz exercise no id', () => {
                comp.quizExercise.id = undefined;
                saveQuizWithPendingChangesCache();
                expect(exerciseSanitizeSpy).toHaveBeenCalledWith(comp.quizExercise);
                expect(quizExerciseServiceCreateStub).toHaveBeenCalledOnce();
                expect(quizExerciseServiceUpdateStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceImportStub).not.toHaveBeenCalled();
            });

            it('should call not update if testruns exist in exam mode', () => {
                comp.quizExercise.testRunParticipationsExist = true;
                comp.isExamMode = true;
                saveQuizWithPendingChangesCache();
                expect(exerciseSanitizeSpy).not.toHaveBeenCalledWith(comp.quizExercise);
                expect(quizExerciseServiceCreateStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceUpdateStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceImportStub).not.toHaveBeenCalled();
            });

            it('should update if valid and quiz exercise has id', () => {
                saveQuizWithPendingChangesCache();
                expect(exerciseSanitizeSpy).toHaveBeenCalledWith(comp.quizExercise);
                expect(quizExerciseServiceCreateStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceUpdateStub).toHaveBeenCalledExactlyOnceWith(comp.quizExercise.id, comp.quizExercise, new Map<string, Blob>(), {});
                expect(quizExerciseServiceImportStub).not.toHaveBeenCalled();
            });

            it('should import if valid and quiz exercise has id and flag', () => {
                comp.isImport = true;
                saveQuizWithPendingChangesCache();
                expect(exerciseSanitizeSpy).toHaveBeenCalledWith(comp.quizExercise);
                expect(quizExerciseServiceCreateStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceUpdateStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceImportStub).toHaveBeenCalledExactlyOnceWith(comp.quizExercise, new Map<string, Blob>());
            });

            it('should not save if not valid', () => {
                comp.quizIsValid = false;
                comp.pendingChangesCache = true;
                comp.save();
                expect(exerciseSanitizeSpy).not.toHaveBeenCalled();
                expect(quizExerciseServiceCreateStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceUpdateStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceImportStub).not.toHaveBeenCalled();
            });

            it('should call update with notification text if there is one', () => {
                comp.notificationText = 'test';
                saveQuizWithPendingChangesCache();
                expect(exerciseSanitizeSpy).toHaveBeenCalledWith(comp.quizExercise);
                expect(quizExerciseServiceCreateStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceUpdateStub).toHaveBeenCalledWith(comp.quizExercise.id, comp.quizExercise, new Map<string, Blob>(), { notificationText: 'test' });
                expect(quizExerciseServiceImportStub).not.toHaveBeenCalled();
            });

            it('should call alert service if response has no body on create', () => {
                comp.quizExercise.id = undefined;
                quizExerciseServiceCreateStub.mockReturnValue(of(new HttpResponse<QuizExercise>({})));
                saveAndExpectAlertService();
            });

            it('should call alert service if response has no body on update', () => {
                quizExerciseServiceUpdateStub.mockReturnValue(of(new HttpResponse<QuizExercise>({})));
                saveAndExpectAlertService();
            });

            it('should call alert service if response has no body on import', () => {
                comp.isImport = true;
                quizExerciseServiceImportStub.mockReturnValue(of(new HttpResponse<QuizExercise>({})));
                saveAndExpectAlertService();
            });

            it('should call alert service if create fails', () => {
                comp.quizExercise.id = undefined;
                quizExerciseServiceCreateStub.mockReturnValue(throwError(() => ({ status: 404 })));
                saveAndExpectAlertService();
            });

            it('should call alert service if update fails', () => {
                quizExerciseServiceUpdateStub.mockReturnValue(throwError(() => ({ status: 404 })));
                saveAndExpectAlertService();
            });

            it('should call alert service if import fails', () => {
                comp.isImport = true;
                quizExerciseServiceImportStub.mockReturnValue(throwError(() => ({ status: 404 })));
                saveAndExpectAlertService();
            });
        });

        describe('routing', () => {
            let routerSpy: jest.SpyInstance;

            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                routerSpy = jest.spyOn(router, 'navigate');
            });

            afterEach(() => {
                jest.clearAllMocks();
            });

            it('should go back to quiz exercise page on cancel', () => {
                comp.quizExercise.course = course;
                comp.previousState();
                expect(routerSpy).toHaveBeenCalledWith(['/course-management', course.id, 'exercises']);
            });

            it('should go back to quiz exercise page on cancel (exam)', () => {
                comp.quizExercise.exerciseGroup = { id: 4, exam: { id: 5, course } };
                comp.previousState();
                expect(routerSpy).toHaveBeenCalledWith(['/course-management', course.id, 'exams', 5, 'exercise-groups']);
            });
        });

        describe('prepare entity', () => {
            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
            });

            it('should set duration to 10 if not given', () => {
                comp.quizExercise.duration = undefined;
                comp.prepareEntity(comp.quizExercise);
                expect(comp.quizExercise.duration).toBe(10);
            });

            it('should set release date to dayjs release date if exam mode', () => {
                comp.isExamMode = true;
                const now = dayjs();
                comp.quizExercise.releaseDate = now;
                comp.prepareEntity(comp.quizExercise);
                expect(comp.quizExercise.releaseDate).toEqual(dayjs(now));
            });
        });

        describe('quiz mode', () => {
            const b1 = new QuizBatch();
            const b2 = new QuizBatch();
            const b3 = new QuizBatch();
            b1.id = 1;
            b2.id = 2;
            b3.id = 3;

            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
            });

            it('should manage batches for synchronized mode', () => {
                comp.cacheValidation();
                expect(quizExercise.quizBatches).toBeArrayOfSize(0);
                comp.scheduleQuizStart = true;
                comp.cacheValidation();
                expect(quizExercise.quizBatches).toBeArrayOfSize(1);
                comp.scheduleQuizStart = false;
                comp.cacheValidation();
                expect(quizExercise.quizBatches).toBeArrayOfSize(0);
            });

            it('should add batches', () => {
                expect(quizExercise.quizBatches).toBeArrayOfSize(0);
                comp.addQuizBatch();
                expect(quizExercise.quizBatches).toBeArrayOfSize(1);
            });

            it('should add batches when none exist', () => {
                quizExercise.quizBatches = undefined;
                comp.addQuizBatch();
                expect(quizExercise.quizBatches).toBeArrayOfSize(1);
            });

            it('should remove batches', () => {
                quizExercise.quizBatches = [b1, b2, b3];
                comp.removeQuizBatch(b2);
                expect(quizExercise.quizBatches).toEqual([b1, b3]);
            });

            it('should not remove batches when they dont exist', () => {
                quizExercise.quizBatches = [b1, b3];
                comp.removeQuizBatch(b2);
                expect(quizExercise.quizBatches).toEqual([b1, b3]);
            });
        });

        describe('invalid reasons', () => {
            const filterReasonAndExpectMoreThanOneInArray = (translateKey: string) => {
                const invalidReasons = comp.computeInvalidReasons().filter((reason) => reason.translateKey === translateKey);
                expect(invalidReasons.length).toBeGreaterThan(0);
            };

            const checkForInvalidFlaggedQuestionAndReason = () => {
                comp.checkForInvalidFlaggedQuestions();
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionHasInvalidFlaggedElements');
            };

            describe('should include right reasons in reasons array for quiz', () => {
                beforeEach(() => {
                    resetQuizExercise();
                    comp.quizExercise = quizExercise;
                });

                it('should put reason for no title', () => {
                    quizExercise.title = '';
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.quizTitle');
                });

                it('should put reason for too long title', () => {
                    quizExercise.title = 'a'.repeat(250);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.quizTitleLength');
                });

                it('should put reason for no duration', () => {
                    quizExercise.duration = 0;
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.quizDuration');
                });

                it('should put reason for no questions', () => {
                    quizExercise.quizQuestions = [];
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.noQuestion');
                });
            });

            describe('should include right reasons in reasons array for MC and general', () => {
                let question: MultipleChoiceQuestion;
                let answerOption1: AnswerOption;
                let answerOption2: AnswerOption;

                beforeEach(() => {
                    resetQuizExercise();
                    comp.quizExercise = quizExercise;
                    const multiChoiceQuestion = createValidMCQuestion();
                    question = multiChoiceQuestion.question;
                    answerOption1 = multiChoiceQuestion.answerOption1;
                    answerOption2 = multiChoiceQuestion.answerOption2;
                    comp.quizExercise.quizQuestions = [question];
                });

                it('should put reason for undefined score', () => {
                    question.points = undefined;
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionScore');
                });

                it('should put reason for negative score', () => {
                    question.points = -1;
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionScoreInvalid');
                });

                it('should put reason for score in invalid range', () => {
                    question.points = 99999999999;
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionScoreInvalid');
                });

                it('should put reason for no title', () => {
                    question.title = '';
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionTitle');
                });

                it('should put reason for no correct answer for MC', () => {
                    answerOption1.isCorrect = false;
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionCorrectAnswerOption');
                });

                it('should put reason for no correct explanation for MC', () => {
                    answerOption1.explanation = '';
                    answerOption2.explanation = '';
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.explanationIsMissing');
                });

                it('should put reason for too long title', () => {
                    question.title = 'a'.repeat(250);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionTitleLength');
                });

                it('should put reason if question title is included in invalid flagged question', () => {
                    answerOption1.invalid = true;
                    checkForInvalidFlaggedQuestionAndReason();
                });

                it('should put reason if question explanation is too long', () => {
                    question.explanation = 'f'.repeat(500 + 1);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionExplanationLength');
                });

                it('should put reason if question hint is too long', () => {
                    question.hint = 'f'.repeat(255 + 1);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionHintLength');
                });

                it('should put reason if answer option explanation is too long', () => {
                    answerOption1.explanation = 'f'.repeat(500 + 1);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.answerExplanationLength');
                });

                it('should put reason if answer option hint is too long', () => {
                    answerOption1.hint = 'f'.repeat(255 + 1);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.answerHintLength');
                });

                it('should put reason exactly once if more than one answer option explanation is too long', () => {
                    answerOption1.explanation = 'f'.repeat(500 + 1);
                    answerOption2.explanation = 'f'.repeat(500 + 1);
                    const invalidReasonsAnswerOptions = comp
                        .computeInvalidReasons()
                        .filter((reason) => reason.translateKey === 'artemisApp.quizExercise.invalidReasons.answerExplanationLength');
                    expect(invalidReasonsAnswerOptions).toHaveLength(1);
                });

                it('should put reason exactly once if more than one answer option hint is too long', () => {
                    answerOption1.hint = 'f'.repeat(255 + 1);
                    answerOption2.hint = 'f'.repeat(255 + 1);
                    const invalidReasonsAnswerOptions = comp
                        .computeInvalidReasons()
                        .filter((reason) => reason.translateKey === 'artemisApp.quizExercise.invalidReasons.answerHintLength');
                    expect(invalidReasonsAnswerOptions).toHaveLength(1);
                });
            });

            describe('should include right reasons in reasons array for DnD', () => {
                let question: DragAndDropQuestion;
                let dragItem1: DragItem;
                let dropLocation: DropLocation;
                let correctDragAndDropMapping: DragAndDropMapping;

                beforeEach(() => {
                    resetQuizExercise();
                    comp.quizExercise = quizExercise;
                    const dndQuestion = createValidDnDQuestion();
                    question = dndQuestion.question;
                    dragItem1 = dndQuestion.dragItem1;
                    correctDragAndDropMapping = dndQuestion.correctDragAndDropMapping;
                    dropLocation = dndQuestion.dropLocation;
                    comp.quizExercise.quizQuestions = [question];
                });

                afterEach(() => {
                    jest.restoreAllMocks();
                });

                it('should put reason for no correct mappings', () => {
                    question.correctMappings = [];
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionCorrectMapping');
                });

                it('should put reason for unsolvable', () => {
                    jest.spyOn(dragAndDropQuestionUtil, 'solve').mockReturnValue([]);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionUnsolvable');
                });

                it('should put reason for misleading correct mappings', () => {
                    jest.spyOn(dragAndDropQuestionUtil, 'validateNoMisleadingCorrectMapping').mockReturnValue(false);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.misleadingCorrectMapping');
                });

                it('should put reason and flag as invalid if a drag item is invalid', () => {
                    dragItem1.invalid = true;
                    checkForInvalidFlaggedQuestionAndReason();
                });

                it('should put reason and flag as invalid if a correct mapping is invalid', () => {
                    correctDragAndDropMapping.invalid = true;
                    checkForInvalidFlaggedQuestionAndReason();
                });

                it('should put reason and flag as invalid if a drop location is invalid', () => {
                    dropLocation.invalid = true;
                    checkForInvalidFlaggedQuestionAndReason();
                });

                it('should put reason if question explanation is too long', () => {
                    question.explanation = 'f'.repeat(500 + 1);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionExplanationLength');
                });

                it('should put reason if question hint is too long', () => {
                    question.hint = 'f'.repeat(255 + 1);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionHintLength');
                });
            });

            describe('should include right reasons in reasons array for SA', () => {
                let question: ShortAnswerQuestion;
                let shortAnswerSolution1: ShortAnswerSolution;
                let shortAnswerMapping1: ShortAnswerMapping;
                let spot1: ShortAnswerSpot;

                beforeEach(() => {
                    resetQuizExercise();
                    comp.quizExercise = quizExercise;
                    const saQuestion = createValidSAQuestion();
                    question = saQuestion.question;
                    shortAnswerSolution1 = saQuestion.shortAnswerSolution1;
                    shortAnswerMapping1 = saQuestion.shortAnswerMapping1;
                    spot1 = saQuestion.spot1;
                    comp.quizExercise.quizQuestions = [question];
                });

                afterEach(() => {
                    jest.restoreAllMocks();
                });

                it('should put reason for no correct mappings', () => {
                    question.correctMappings = [];
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionCorrectMapping');
                });

                it('should put reason for misleading correct mappings', () => {
                    jest.spyOn(shortAnswerQuestionUtil, 'validateNoMisleadingShortAnswerMapping').mockReturnValue(false);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.misleadingCorrectMapping');
                });

                it('should put reason when every spot has a solution', () => {
                    jest.spyOn(shortAnswerQuestionUtil, 'everySpotHasASolution').mockReturnValue(false);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.shortAnswerQuestionEverySpotHasASolution');
                });

                it('should put reason when every mapped solution has a spot', () => {
                    jest.spyOn(shortAnswerQuestionUtil, 'everyMappedSolutionHasASpot').mockReturnValue(false);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.shortAnswerQuestionEveryMappedSolutionHasASpot');
                });

                it('should put reason when there is an empty solution', () => {
                    shortAnswerSolution1.text = '';
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.shortAnswerQuestionSolutionHasNoValue');
                });

                it('should put reason for too long answer option', () => {
                    shortAnswerSolution1.text = 'a'.repeat(250);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.quizAnswerOptionLength');
                });

                it('should put reason when duplicate mappings', () => {
                    jest.spyOn(shortAnswerQuestionUtil, 'hasMappingDuplicateValues').mockReturnValue(true);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.shortAnswerQuestionDuplicateMapping');
                });

                it('should put reason for not many solutions as spots', () => {
                    jest.spyOn(shortAnswerQuestionUtil, 'atLeastAsManySolutionsAsSpots').mockReturnValue(false);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.shortAnswerQuestionUnsolvable');
                });

                it('should put reason and flag as invalid if a solution is invalid', () => {
                    shortAnswerSolution1.invalid = true;
                    checkForInvalidFlaggedQuestionAndReason();
                });

                it('should put reason and flag as invalid if a answer mapping is invalid', () => {
                    shortAnswerMapping1.invalid = true;
                    checkForInvalidFlaggedQuestionAndReason();
                });

                it('should put reason and flag as invalid if a spot is invalid', () => {
                    spot1.invalid = true;
                    checkForInvalidFlaggedQuestionAndReason();
                });
            });
        });
    });
});
