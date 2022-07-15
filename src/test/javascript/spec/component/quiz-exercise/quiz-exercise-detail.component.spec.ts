import { HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef, EventEmitter, SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { expect as jestExpect } from '@jest/globals';
import { NgbDate, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
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
import { QuizBatch, QuizExercise, QuizMode } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { QuizExerciseDetailComponent } from 'app/exercises/quiz/manage/quiz-exercise-detail.component';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
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
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { MockProvider } from 'ng-mocks';
import { Duration } from 'app/exercises/quiz/manage/quiz-exercise-interfaces';

describe('QuizExercise Management Detail Component', () => {
    let comp: QuizExerciseDetailComponent;
    let exerciseGroupService: ExerciseGroupService;
    let courseManagementService: CourseManagementService;
    let examManagementService: ExamManagementService;
    let quizExerciseService: QuizExerciseService;
    let exerciseService: ExerciseService;
    let fileUploaderService: FileUploaderService;
    let fixture: ComponentFixture<QuizExerciseDetailComponent>;
    let router: Router;
    let alertService: AlertService;
    let dragAndDropQuestionUtil: DragAndDropQuestionUtil;
    let shortAnswerQuestionUtil: ShortAnswerQuestionUtil;
    let changeDetector: ChangeDetectorRef;
    let modalService: NgbModal;

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
        quizExercise.quizMode = QuizMode.SYNCHRONIZED;
    };

    resetQuizExercise();

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: quizExercise.id }) } } as any as ActivatedRoute;

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
            declarations: [QuizExerciseDetailComponent],
            providers: [
                MockProvider(NgbModal),
                MockProvider(ChangeDetectorRef),
                MockProvider(DragAndDropQuestionUtil),
                { provide: ActivatedRoute, useValue: testRoute || route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
            ],
        })
            .overrideTemplate(QuizExerciseDetailComponent, '')
            .compileComponents();
    };

    const configureFixtureAndServices = () => {
        fixture = TestBed.createComponent(QuizExerciseDetailComponent);
        comp = fixture.componentInstance;
        courseManagementService = fixture.debugElement.injector.get(CourseManagementService);
        examManagementService = fixture.debugElement.injector.get(ExamManagementService);
        quizExerciseService = fixture.debugElement.injector.get(QuizExerciseService);
        router = fixture.debugElement.injector.get(Router);
        fileUploaderService = TestBed.inject(FileUploaderService);
        alertService = fixture.debugElement.injector.get(AlertService);
        dragAndDropQuestionUtil = fixture.debugElement.injector.get(DragAndDropQuestionUtil);
        shortAnswerQuestionUtil = fixture.debugElement.injector.get(ShortAnswerQuestionUtil);
        changeDetector = fixture.debugElement.injector.get(ChangeDetectorRef);
        exerciseGroupService = fixture.debugElement.injector.get(ExerciseGroupService);
        exerciseService = fixture.debugElement.injector.get(ExerciseService);
        modalService = fixture.debugElement.injector.get(NgbModal);
    };

    describe('OnInit', () => {
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
            it('Should call courseExerciseService.find and quizExerciseService.find', () => {
                // GIVEN
                configureStubs();
                // WHEN
                comp.course = course;
                comp.ngOnInit();

                // THEN
                expect(quizExerciseServiceStub).toBeCalled();
                expect(courseManagementServiceStub).toBeCalled();
                expect(exerciseGroupServiceStub).not.toBeCalled();
                expect(initStub).toBeCalled();
            });

            afterEach(() => {
                jest.clearAllMocks();
            });
        });

        describe('with exam id', () => {
            const testRoute = {
                snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: quizExercise.id, examId: 1, exerciseGroupId: 2 }) },
            } as any as ActivatedRoute;
            beforeEach(waitForAsync(() => configureTestBed(testRoute)));
            beforeEach(configureFixtureAndServices);
            it('should call exerciseGroupService.find', () => {
                configureStubs();
                comp.course = course;
                comp.ngOnInit();
                expect(quizExerciseServiceStub).toBeCalled();
                expect(courseManagementServiceStub).toBeCalled();
                expect(exerciseGroupServiceStub).toBeCalled();
                expect(initStub).toBeCalled();
            });

            afterEach(() => {
                jest.clearAllMocks();
            });
        });

        describe('with exam id but without exercise id', () => {
            const testRoute = { snapshot: { paramMap: convertToParamMap({ courseId: course.id, examId: 1, exerciseGroupId: 2 }) } } as any as ActivatedRoute;
            beforeEach(waitForAsync(() => configureTestBed(testRoute)));
            beforeEach(configureFixtureAndServices);
            it('should call exerciseGroupService.find', () => {
                configureStubs();
                comp.course = course;
                comp.ngOnInit();
                expect(quizExerciseServiceStub).not.toBeCalled();
                expect(courseManagementServiceStub).toBeCalled();
                expect(exerciseGroupServiceStub).toBeCalled();
                expect(initStub).toBeCalled();
            });

            afterEach(() => {
                jest.clearAllMocks();
            });
        });
        describe('without exam id and  exercise id', () => {
            const testRoute = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;
            beforeEach(waitForAsync(() => configureTestBed(testRoute)));
            beforeEach(configureFixtureAndServices);
            it('should call exerciseGroupService.find', () => {
                configureStubs();
                comp.course = course;
                comp.ngOnInit();
                expect(quizExerciseServiceStub).not.toBeCalled();
                expect(courseManagementServiceStub).toBeCalled();
                expect(exerciseGroupServiceStub).not.toBeCalled();
                expect(initStub).toBeCalled();
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
                expect(comp.quizExercise.dueDateError).toBeFalse();
                expect(comp.hasErrorInQuizBatches()).toBeFalse();

                comp.quizExercise!.quizBatches![0].startTime = now.add(1, 'days');

                comp.validateDate();
                expect(comp.quizExercise.dueDateError).toBeFalse();
                expect(comp.hasErrorInQuizBatches()).toBeFalse();

                comp.quizExercise.dueDate = now.add(2, 'days');

                comp.validateDate();
                expect(comp.quizExercise.dueDateError).toBeFalse();
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
                expect(comp.quizExercise.dueDateError).toBeFalse();
                expect(comp.hasErrorInQuizBatches()).toBeTrue();

                comp.quizExercise.dueDate = now.add(-2, 'days');

                comp.validateDate();
                expect(comp.quizExercise.dueDateError).toBeTrue();
                expect(comp.hasErrorInQuizBatches()).toBeTrue();

                comp.quizExercise!.quizBatches![0].startTime = now.add(1, 'days');
                comp.quizExercise.dueDate = now.add(1, 'days');

                comp.validateDate();
                expect(comp.quizExercise.dueDateError).toBeFalse();
                if (quizMode !== QuizMode.SYNCHRONIZED) {
                    // dueDate for SYNCHRONIZED quizzes are calculated so no need to validate.
                    expect(comp.hasErrorInQuizBatches()).toBeTrue();
                }
            });
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
                expect(comp.quizExercise).toBe(undefined);
                comp.init();
                expect(comp.quizExercise).not.toBe(undefined);
                expect(prepareEntitySpy).toBeCalledWith(comp.quizExercise);
                expect(comp.savedEntity).toEqual(new QuizExercise(undefined, undefined));
                expect(comp.quizExercise.course).toEqual(course);
                expect(courseServiceStub).toBeCalledWith(course.id);
            });

            it('should set entity to quiz exercise if quiz exercise defined', () => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                comp.quizExercise.course = course;
                expect(comp.entity).toBe(undefined);
                comp.init();
                expect(comp.entity).toEqual(quizExercise);
                expect(prepareEntitySpy).toBeCalledWith(comp.quizExercise);
                expect(comp.savedEntity).toEqual(quizExercise);
            });

            it('should set quizExercise exercise group if exam and it does not have one', () => {
                comp.isExamMode = true;
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                expect(comp.quizExercise.exerciseGroup).toBe(undefined);
                comp.exerciseGroup = new ExerciseGroup();
                comp.init();
                expect(comp.quizExercise.exerciseGroup).toEqual(comp.exerciseGroup);
            });

            it('should call on error if course service fails', () => {
                courseServiceStub.mockReturnValue(throwError(() => ({ status: 404 })));
                comp.init();
                expect(alertServiceStub).toBeCalled();
            });
        });

        describe('onDurationChange', () => {
            // setup
            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
            });

            it('Should update duration and quizExercise.duration with same values', () => {
                comp.duration = { minutes: 15, seconds: 30 };
                comp.onDurationChange();

                // compare duration with quizExercise.duration
                const durationAsSeconds = dayjs.duration(comp.duration).asSeconds();
                expect(durationAsSeconds).toEqual(comp.quizExercise.duration);
            });

            it('Should increase minutes when reaching 60 seconds', () => {
                comp.duration = { minutes: 0, seconds: 60 };
                comp.onDurationChange();

                expect(comp.duration.minutes).toBe(1);
                expect(comp.duration.seconds).toBe(0);
            });

            it('Should decrease minutes when reaching -1 seconds', () => {
                comp.duration = { minutes: 1, seconds: -1 };
                comp.onDurationChange();

                expect(comp.duration.minutes).toBe(0);
                expect(comp.duration.seconds).toBe(59);
            });

            it('Should set duration to due date release date difference', () => {
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
                expect(initStub).toBeCalled();
                initStub.mockClear();

                comp.ngOnChanges({ quizExercise: change });
                expect(initStub).toBeCalled();
                initStub.mockClear();

                comp.ngOnChanges({ course: change, quizExercise: change });
                expect(initStub).toBeCalled();
                initStub.mockClear();

                comp.ngOnChanges({});
                expect(initStub).not.toBeCalled();
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
                expect(comp.quizExercise).toBe(undefined);
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
                expect(ev.returnValue).toBe(undefined);
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

        describe('add questions', () => {
            // setup
            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
            });

            it('should add empty MC question', () => {
                const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
                comp.addMultipleChoiceQuestion();

                expect(comp.quizExercise.quizQuestions).toHaveLength(amountQuizQuestions + 1);
                expect(comp.quizExercise.quizQuestions?.last()?.type).toEqual(QuizQuestionType.MULTIPLE_CHOICE);
            });

            it('should add empty DnD question', () => {
                const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
                comp.addDragAndDropQuestion();

                expect(comp.quizExercise.quizQuestions).toHaveLength(amountQuizQuestions + 1);
                expect(comp.quizExercise.quizQuestions?.last()?.type).toEqual(QuizQuestionType.DRAG_AND_DROP);
            });

            it('should add empty SA question', () => {
                const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
                comp.addShortAnswerQuestion();

                expect(comp.quizExercise.quizQuestions).toHaveLength(amountQuizQuestions + 1);
                expect(comp.quizExercise.quizQuestions?.last()?.type).toEqual(QuizQuestionType.SHORT_ANSWER);
            });
        });

        describe('add questions without quizExercise', () => {
            beforeEach(() => {
                comp.entity = quizExercise;
            });

            it('should set quiz exercise to entity when adding MC question', () => {
                expect(comp.quizExercise).toBeUndefined();
                comp.addMultipleChoiceQuestion();
                expect(comp.quizExercise).toEqual(comp.entity);
            });

            it('should set quiz exercise to entity when adding Dnd question', () => {
                expect(comp.quizExercise).toBeUndefined();
                comp.addDragAndDropQuestion();
                expect(comp.quizExercise).toEqual(comp.entity);
            });

            it('should set quiz exercise to entity when adding SA question', () => {
                expect(comp.quizExercise).toBeUndefined();
                comp.addShortAnswerQuestion();
                expect(comp.quizExercise).toEqual(comp.entity);
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

        describe('add existing questions', () => {
            it('should add questions with export quiz option and call import', () => {
                comp.showExistingQuestions = true;
                const { question: exportedQuestion } = createValidMCQuestion();
                exportedQuestion.title = 'exported';
                exportedQuestion.exportQuiz = true;
                const { question: notExportedQuestion } = createValidMCQuestion();
                notExportedQuestion.title = 'notExported';
                notExportedQuestion.exportQuiz = false;
                comp.existingQuestions = [exportedQuestion, notExportedQuestion];
                const verifyAndImportQuestionsStub = jest.spyOn(comp, 'verifyAndImportQuestions').mockImplementation();
                const cacheValidationStub = jest.spyOn(comp, 'cacheValidation').mockImplementation();
                comp.addExistingQuestions();
                expect(verifyAndImportQuestionsStub).toBeCalledWith([exportedQuestion]);
                expect(cacheValidationStub).toBeCalled();
                expect(comp.showExistingQuestions).toBeFalse();
                expect(comp.showExistingQuestionsFromCourse).toBeTrue();
                expect(comp.selectedCourseId).toBeUndefined();
                expect(comp.allExistingQuestions).toEqual([]);
                expect(comp.existingQuestions).toEqual([]);

                // restore mocks
                verifyAndImportQuestionsStub.mockRestore();
                cacheValidationStub.mockRestore();
            });

            describe('applyFilter', () => {
                const { question: multiChoiceQuestion } = createValidMCQuestion();
                const { question: dndQuestion } = createValidDnDQuestion();
                const { question: shortQuestion } = createValidSAQuestion();

                beforeEach(() => {
                    comp.quizExercise = quizExercise;
                    comp.allExistingQuestions = [multiChoiceQuestion, dndQuestion, shortQuestion];
                    comp.mcqFilterEnabled = false;
                    comp.dndFilterEnabled = false;
                    comp.shortAnswerFilterEnabled = false;
                    comp.searchQueryText = '';
                });

                it('should put mc question when mc filter selected', () => {
                    comp.mcqFilterEnabled = true;
                    comp.applyFilter();
                    expect(comp.existingQuestions).toEqual([multiChoiceQuestion]);
                });

                it('should put mc question when dnd filter selected', () => {
                    comp.dndFilterEnabled = true;
                    comp.applyFilter();
                    expect(comp.existingQuestions).toEqual([dndQuestion]);
                });

                it('should put mc question when sa filter selected', () => {
                    comp.shortAnswerFilterEnabled = true;
                    comp.applyFilter();
                    expect(comp.existingQuestions).toEqual([shortQuestion]);
                });

                it('should put all if all selected', () => {
                    comp.mcqFilterEnabled = true;
                    comp.dndFilterEnabled = true;
                    comp.shortAnswerFilterEnabled = true;
                    comp.applyFilter();
                    expect(comp.existingQuestions).toEqual(comp.allExistingQuestions);
                });
            });

            describe('select course', () => {
                let quizExerciseServiceFindForCourseStub: jest.SpyInstance;
                let quizExerciseServiceFindStub: jest.SpyInstance;

                beforeEach(() => {
                    comp.allExistingQuestions = [];
                    comp.courses = [course];
                    comp.selectedCourseId = course.id;
                    resetQuizExercise();
                    comp.quizExercise = quizExercise;
                    quizExerciseServiceFindForCourseStub = jest.spyOn(quizExerciseService, 'findForCourse');
                    quizExerciseServiceFindForCourseStub.mockReturnValue(of(new HttpResponse<QuizExercise[]>({ body: [quizExercise] })));
                    quizExerciseServiceFindStub = jest.spyOn(quizExerciseService, 'find');
                    quizExerciseServiceFindStub.mockReturnValue(of(new HttpResponse<QuizExercise>({ body: quizExercise })));
                });

                it('should call find course with selected id', () => {
                    comp.onCourseSelect();
                    expect(quizExerciseServiceFindForCourseStub).toBeCalledWith(comp.selectedCourseId);
                    expect(quizExerciseServiceFindStub).toBeCalledWith(quizExercise.id);
                    expect(comp.allExistingQuestions).toEqual(quizExercise.quizQuestions);
                });

                it('should not call find course without selected id', () => {
                    comp.selectedCourseId = undefined;
                    comp.onCourseSelect();
                    expect(quizExerciseServiceFindForCourseStub).not.toHaveBeenCalled();
                    expect(quizExerciseServiceFindStub).not.toHaveBeenCalled();
                });

                it('should call alert service if fails', () => {
                    quizExerciseServiceFindForCourseStub.mockReturnValue(throwError(() => ({ status: 404 })));
                    console.error = jest.fn();
                    let alertServiceStub: jest.SpyInstance;
                    alertServiceStub = jest.spyOn(alertService, 'error');
                    comp.onCourseSelect();
                    expect(alertServiceStub).toBeCalled();
                });

                afterAll(() => {
                    jest.clearAllMocks();
                });
            });

            describe('select exam', () => {
                let quizExerciseServiceFindForExamStub: jest.SpyInstance;
                let quizExerciseServiceFindStub: jest.SpyInstance;
                const exerciseGroup = new ExerciseGroup();

                beforeEach(() => {
                    comp.allExistingQuestions = [];
                    exerciseGroup.exam = exam;
                    quizExercise.exerciseGroup = exerciseGroup;
                    comp.exams = [exam];
                    comp.selectedExamId = exam.id;
                    resetQuizExercise();
                    comp.quizExercise = quizExercise;
                    quizExerciseServiceFindForExamStub = jest.spyOn(quizExerciseService, 'findForExam');
                    quizExerciseServiceFindForExamStub.mockReturnValue(of(new HttpResponse<QuizExercise[]>({ body: [quizExercise] })));
                    quizExerciseServiceFindStub = jest.spyOn(quizExerciseService, 'find');
                    quizExerciseServiceFindStub.mockReturnValue(of(new HttpResponse<QuizExercise>({ body: quizExercise })));
                });

                it('should call find exam with selected id', () => {
                    comp.onExamSelect();
                    expect(quizExerciseServiceFindForExamStub).toBeCalledWith(comp.selectedExamId);
                    expect(quizExerciseServiceFindStub).toBeCalledWith(quizExercise.id);
                    expect(comp.allExistingQuestions).toEqual(quizExercise.quizQuestions);
                });

                it('should not call find exam without selected id', () => {
                    comp.selectedExamId = undefined;
                    comp.onExamSelect();
                    expect(quizExerciseServiceFindForExamStub).not.toHaveBeenCalled();
                    expect(quizExerciseServiceFindStub).not.toHaveBeenCalled();
                });

                it('should call alert service if fails', () => {
                    quizExerciseServiceFindForExamStub.mockReturnValue(throwError(() => ({ status: 404 })));
                    console.error = jest.fn();
                    let alertServiceStub: jest.SpyInstance;
                    alertServiceStub = jest.spyOn(alertService, 'error');
                    comp.onExamSelect();
                    expect(alertServiceStub).toBeCalled();
                });

                afterAll(() => {
                    jest.clearAllMocks();
                });
            });
        });

        describe('delete questions', () => {
            const deleteQuestionAndExpect = () => {
                const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
                const questionToDelete = comp.quizExercise.quizQuestions![amountQuizQuestions - 1];
                comp.deleteQuestion(questionToDelete);
                expect(comp.quizExercise.quizQuestions).toHaveLength(amountQuizQuestions - 1);
                expect(comp.quizExercise.quizQuestions?.filter((question) => question === questionToDelete));
            };

            // setup
            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
            });

            it('should delete MC question', () => {
                comp.addMultipleChoiceQuestion();
                deleteQuestionAndExpect();
            });

            it('should delete DnD question', () => {
                comp.addDragAndDropQuestion();
                deleteQuestionAndExpect();
            });

            it('should delete SA question', () => {
                comp.addShortAnswerQuestion();
                deleteQuestionAndExpect();
            });
        });

        describe('updating question', () => {
            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
            });

            it('should replace quiz questions with copy of it', () => {
                const cacheValidationStub = jest.spyOn(comp, 'cacheValidation');
                comp.onQuestionUpdated();
                expect(cacheValidationStub).toBeCalled();
                expect(comp.quizExercise.quizQuestions).toEqual(Array.from(comp.quizExercise.quizQuestions!));
            });
        });

        describe('import questions', () => {
            const importQuestionAndExpectOneMoreQuestionInQuestions = async (question: QuizQuestion) => {
                const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
                await comp.verifyAndImportQuestions([question]);

                expect(comp.quizExercise.quizQuestions).toHaveLength(amountQuizQuestions + 1);
            };
            // setup
            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
            });

            it('should set import file correctly', () => {
                const file = new File(['content'], 'testFileName', { type: 'text/plain' });
                const ev = { target: { files: [file] } };
                const changeDetectorDetectChangesStub = jest.spyOn(changeDetector.constructor.prototype, 'detectChanges');
                comp.setImportFile(ev);
                expect(comp.importFile).toEqual(file);
                expect(comp.importFileName).toBe('testFileName');
                expect(changeDetectorDetectChangesStub).toBeCalled();
            });

            it('should import MC question ', async () => {
                const { question, answerOption1, answerOption2 } = createValidMCQuestion();
                await importQuestionAndExpectOneMoreQuestionInQuestions(question);
                const lastAddedQuestion = comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1] as MultipleChoiceQuestion;
                expect(lastAddedQuestion.type).toEqual(QuizQuestionType.MULTIPLE_CHOICE);
                expect(lastAddedQuestion.answerOptions).toHaveLength(2);
                expect(lastAddedQuestion.answerOptions![0]).toEqual(answerOption1);
                expect(lastAddedQuestion.answerOptions![1]).toEqual(answerOption2);
            });

            it('should import DnD question', async () => {
                const { question, dragItem1, dragItem2, dropLocation } = createValidDnDQuestion();

                // mock fileUploaderService
                jest.spyOn(fileUploaderService, 'duplicateFile').mockReturnValue(Promise.resolve({ path: 'test' }));
                await importQuestionAndExpectOneMoreQuestionInQuestions(question);
                const lastAddedQuestion = comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1] as DragAndDropQuestion;
                expect(lastAddedQuestion.type).toEqual(QuizQuestionType.DRAG_AND_DROP);
                expect(lastAddedQuestion.correctMappings).toHaveLength(1);
                expect(lastAddedQuestion.dragItems![0]).toEqual(dragItem1);
                expect(lastAddedQuestion.dragItems![1]).toEqual(dragItem2);
                expect(lastAddedQuestion.dropLocations![0]).toEqual(dropLocation);
                expect(lastAddedQuestion.dragItems![0].pictureFilePath).toBe('test');
                expect(lastAddedQuestion.dragItems![1].pictureFilePath).toBe(undefined);
            });

            it('should import SA question', async () => {
                const { question, shortAnswerMapping1, shortAnswerMapping2, spot1, spot2, shortAnswerSolution1, shortAnswerSolution2 } = createValidSAQuestion();
                importQuestionAndExpectOneMoreQuestionInQuestions(question);
                const lastAddedQuestion = comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1] as ShortAnswerQuestion;
                expect(lastAddedQuestion.type).toEqual(QuizQuestionType.SHORT_ANSWER);
                expect(lastAddedQuestion.correctMappings).toHaveLength(2);
                expect(lastAddedQuestion.correctMappings![0]).toEqual(shortAnswerMapping1);
                expect(lastAddedQuestion.correctMappings![1]).toEqual(shortAnswerMapping2);
                expect(lastAddedQuestion.spots).toHaveLength(2);
                expect(lastAddedQuestion.spots![0]).toEqual(spot1);
                expect(lastAddedQuestion.spots![1]).toEqual(spot2);
                expect(lastAddedQuestion.solutions).toHaveLength(2);
                expect(lastAddedQuestion.solutions![0]).toEqual(shortAnswerSolution1);
                expect(lastAddedQuestion.solutions![1]).toEqual(shortAnswerSolution2);
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
        });

        describe('saving', () => {
            let quizExerciseServiceCreateStub: jest.SpyInstance;
            let quizExerciseServiceUpdateStub: jest.SpyInstance;
            let exerciseStub: jest.SpyInstance;
            const saveQuizWithPendingChangesCache = () => {
                comp.cacheValidation();
                comp.pendingChangesCache = true;
                comp.save();
            };

            const saveAndExpectAlertService = () => {
                console.error = jest.fn();
                let alertServiceStub: jest.SpyInstance;
                alertServiceStub = jest.spyOn(alertService, 'error');
                saveQuizWithPendingChangesCache();
                expect(alertServiceStub).toBeCalled();
                expect(comp.isSaving).toBeFalse();
                jestExpect(console.error).toHaveBeenCalled();
            };

            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                quizExerciseServiceCreateStub = jest.spyOn(quizExerciseService, 'create');
                quizExerciseServiceCreateStub.mockReturnValue(of(new HttpResponse<QuizExercise>({ body: quizExercise })));
                quizExerciseServiceUpdateStub = jest.spyOn(quizExerciseService, 'update');
                quizExerciseServiceUpdateStub.mockReturnValue(of(new HttpResponse<QuizExercise>({ body: quizExercise })));
                exerciseStub = jest.spyOn(Exercise, 'sanitize');
            });

            afterEach(() => {
                jest.clearAllMocks();
            });

            it('should call create if valid and quiz exercise no id', () => {
                comp.quizExercise.id = undefined;
                saveQuizWithPendingChangesCache();
                expect(exerciseStub).toBeCalledWith(comp.quizExercise);
                expect(quizExerciseServiceCreateStub).toBeCalled();
                expect(quizExerciseServiceUpdateStub).not.toHaveBeenCalled();
            });

            it('should call not update if testruns exist in exam mode', () => {
                comp.quizExercise.testRunParticipationsExist = true;
                comp.isExamMode = true;
                saveQuizWithPendingChangesCache();
                expect(exerciseStub).not.toHaveBeenCalledWith(comp.quizExercise);
                expect(quizExerciseServiceCreateStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceUpdateStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceUpdateStub).not.toHaveBeenCalledWith(comp.quizExercise, {});
            });

            it('should update if valid and quiz exercise has id', () => {
                saveQuizWithPendingChangesCache();
                expect(exerciseStub).toBeCalledWith(comp.quizExercise);
                expect(quizExerciseServiceCreateStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceUpdateStub).toBeCalled();
                expect(quizExerciseServiceUpdateStub).toBeCalledWith(comp.quizExercise, {});
            });

            it('should not save if not valid', () => {
                comp.quizIsValid = false;
                comp.pendingChangesCache = true;
                comp.save();
                expect(exerciseStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceCreateStub).not.toHaveBeenCalled();
                expect(quizExerciseServiceUpdateStub).not.toHaveBeenCalled();
            });

            it('should call update with notification text if there is one', () => {
                comp.notificationText = 'test';
                saveQuizWithPendingChangesCache();
                expect(quizExerciseServiceUpdateStub).toBeCalledWith(comp.quizExercise, { notificationText: 'test' });
            });

            it('should call alert service if response has no body on update', () => {
                quizExerciseServiceUpdateStub.mockReturnValue(of(new HttpResponse<QuizExercise>({})));
                saveAndExpectAlertService();
            });

            it('should call alert service if response has no body on create', () => {
                comp.quizExercise.id = undefined;
                quizExerciseServiceCreateStub.mockReturnValue(of(new HttpResponse<QuizExercise>({})));
                saveAndExpectAlertService();
            });

            it('should call alert service if update fails', () => {
                quizExerciseServiceUpdateStub.mockReturnValue(throwError(() => ({ status: 404 })));
                saveAndExpectAlertService();
            });

            it('should call alert service if response has no body on create', () => {
                comp.quizExercise.id = undefined;
                quizExerciseServiceCreateStub.mockReturnValue(throwError(() => ({ status: 404 })));
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
                comp.cancel();
                expect(routerSpy).toBeCalledWith(['/course-management', comp.courseId, 'quiz-exercises']);
            });

            it('should go back to quiz exercise page on cancel', () => {
                comp.isExamMode = true;
                comp.cancel();
                expect(routerSpy).toBeCalledWith(['/course-management', comp.courseId, 'exams', comp.examId, 'exercise-groups']);
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

        describe('show existing questions', () => {
            let courseManagementServiceStub: jest.SpyInstance;
            let examManagementServiceStub: jest.SpyInstance;

            beforeEach(() => {
                comp.courseRepository = courseManagementService;
                courseManagementServiceStub = jest.spyOn(comp.courseRepository, 'getAllCoursesWithQuizExercises');
                courseManagementServiceStub.mockReturnValue(of(new HttpResponse<Course>({ body: course })));
                examManagementServiceStub = jest.spyOn(examManagementService, 'findAllExamsAccessibleToUser');
                examManagementServiceStub.mockReturnValue(of(new HttpResponse<Exam>({ body: exam })));
            });

            it('should show existing questions if not already shown', () => {
                comp.courses = [];
                comp.quizExercise = quizExercise;
                comp.showExistingQuestions = false;
                const setQuestionsFromCourseSpy = jest.spyOn(comp, 'setExistingQuestionSourceToCourse');
                comp.showHideExistingQuestions();
                expect(courseManagementServiceStub).toBeCalled();
                expect(examManagementServiceStub).toBeCalled();
                expect(comp.showExistingQuestions).toBeTrue();
                expect(setQuestionsFromCourseSpy).toBeCalled();
            });

            it('should not call getAll if there are courses', () => {
                comp.courses = [course];
                comp.quizExercise = quizExercise;
                comp.showHideExistingQuestions();
                expect(courseManagementServiceStub).not.toHaveBeenCalled();
                expect(examManagementServiceStub).toBeCalled();
            });

            it('should initialize quizExercise if it is not', () => {
                comp.courses = [course];
                comp.entity = quizExercise;
                expect(comp.quizExercise).toBeUndefined();
                comp.showHideExistingQuestions();
                expect(comp.quizExercise).not.toBeUndefined();
            });

            it('should hide existing questions if already shown', () => {
                comp.showExistingQuestions = true;
                comp.showHideExistingQuestions();
                expect(comp.showExistingQuestions).toBeFalse();
            });

            it('should set showExistingQuestionsFromCourse to given value', () => {
                const element = document.createElement('input');
                const control = { ...element, value: 'test' };
                const getElementStub = jest.spyOn(document, 'getElementById').mockReturnValue(control);
                comp.setExistingQuestionSourceToCourse();
                expect(comp.showExistingQuestionsFromCourse).toBeTrue();
                expect(comp.showExistingQuestionsFromFile).toBeFalse();
                expect(comp.showExistingQuestionsFromExam).toBeFalse();
                comp.setExistingQuestionSourceToFile();
                expect(comp.showExistingQuestionsFromCourse).toBeFalse();
                expect(comp.showExistingQuestionsFromFile).toBeTrue();
                expect(comp.showExistingQuestionsFromExam).toBeFalse();
                comp.setExistingQuestionSourceToExam();
                expect(comp.showExistingQuestionsFromCourse).toBeFalse();
                expect(comp.showExistingQuestionsFromFile).toBeFalse();
                expect(comp.showExistingQuestionsFromExam).toBeTrue();
                expect(getElementStub).toBeCalled();
                expect(control.value).toBe('');
            });
        });

        describe('importing quiz', () => {
            let generateFileReaderStub: jest.SpyInstance;
            let verifyStub: jest.SpyInstance;
            let getElementStub: jest.SpyInstance;
            let readAsText: jest.Mock;
            let reader: FileReader;
            const jsonContent = `[{
                "type": "multiple-choice",
                "id": 1,
                "title": "vav",
                "text": "Enter your long question if needed",
                "hint": "Add a hint here (visible during the quiz via ?-Button)",
                "score": 1,
                "scoringType": "ALL_OR_NOTHING",
                "randomizeOrder": true,
                "invalid": false,
                "answerOptions": [
                  {
                    "id": 1,
                    "text": "Enter a correct answer option here",
                    "hint": "Add a hint here (visible during the quiz via ?-Button)",
                    "explanation": "Add an explanation here (only visible in feedback after quiz has ended)",
                    "isCorrect": true,
                    "invalid": false
                  },
                  {
                    "id": 2,
                    "text": "Enter a wrong answer option here",
                    "isCorrect": false,
                    "invalid": false
                  }
                ]
              }]`;
            const fakeFile = new File([jsonContent], 'file.txt', { type: 'text/plain' });
            const questions = JSON.parse(jsonContent) as QuizQuestion[];
            const element = document.createElement('input');
            const control = { ...element, value: 'test' };
            beforeEach(() => {
                comp.importFile = fakeFile;
                comp.quizExercise = quizExercise;
                verifyStub = jest.spyOn(comp, 'verifyAndImportQuestions').mockImplementation();
                readAsText = jest.fn();
                reader = new FileReader();
                reader = { ...reader, result: jsonContent };
                generateFileReaderStub = jest.spyOn(comp, 'generateFileReader').mockReturnValue({ ...reader, onload: null, readAsText });
                getElementStub = jest.spyOn(document, 'getElementById').mockReturnValue(control);
            });

            afterEach(() => {
                jest.clearAllMocks();
            });

            it('should call verify and import questions with right json', async () => {
                expect(control.value).toBe('test');
                await comp.importQuiz();
                jestExpect(readAsText).toHaveBeenCalledWith(fakeFile);
                expect(generateFileReaderStub).toBeCalled();
                comp.onFileLoadImport(reader);
                expect(verifyStub).toBeCalledWith(questions);
                expect(comp.importFile).toBeUndefined();
                expect(getElementStub).toBeCalled();
                expect(control.value).toBe('');
            });

            it('should not call any functions without import file', async () => {
                comp.importFile = undefined;
                await comp.importQuiz();
                jestExpect(readAsText).toHaveBeenCalledTimes(0);
                expect(generateFileReaderStub).not.toHaveBeenCalled();
                expect(comp.importFile).toBeUndefined();
            });

            it('should alert user when onload throws error', async () => {
                const alert = window.alert;
                const alertFunction = jest.fn();
                window.alert = alertFunction;
                verifyStub.mockImplementation(() => {
                    throw '';
                });
                await comp.importQuiz();
                comp.onFileLoadImport(reader);
                jestExpect(alertFunction).toHaveBeenCalled();
                window.alert = alert;
            });
        });

        describe('verify and import questions', () => {
            const { question: multiQuestion, answerOption1 } = createValidMCQuestion();
            let modalServiceStub: jest.SpyInstance;
            let componentInstance: any;
            let shouldImportEmitter: EventEmitter<void>;

            beforeEach(() => {
                shouldImportEmitter = new EventEmitter<void>();
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                componentInstance = { questions: [], shouldImport: shouldImportEmitter };
                modalServiceStub = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ componentInstance });
            });

            it('should call addQuestions', () => {
                const addQuestionsSpy = jest.spyOn(comp, 'addQuestions');
                comp.verifyAndImportQuestions([multiQuestion]);
                expect(addQuestionsSpy).toBeCalledWith([multiQuestion]);
            });

            it('should open modal service when there are invalid questions', () => {
                const addQuestionsSpy = jest.spyOn(comp, 'addQuestions');
                answerOption1.invalid = true;
                comp.verifyAndImportQuestions([multiQuestion]);
                expect(addQuestionsSpy).not.toHaveBeenCalled();
                expect(modalServiceStub).toBeCalled();
                shouldImportEmitter.emit();
                expect(addQuestionsSpy).toBeCalled();
            });
        });

        describe('generating file reader', () => {
            it('should return file reader when called', () => {
                expect(comp.generateFileReader()).toEqual(new FileReader());
            });
        });

        describe('invalid reasons', () => {
            const filterReasonAndExpectMoreThanOneInArray = (translateKey: string) => {
                const invalidReasons = comp.computeInvalidReasons().filter((reason) => reason.translateKey === translateKey);
                expect(invalidReasons.length).toBeGreaterThan(0);
            };

            const checkForInvalidFlaggedQuestionAndReason = () => {
                comp.checkForInvalidFlaggedQuestions(comp.quizExercise.quizQuestions);
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionHasInvalidFlaggedElements');
            };

            it('should concatenate invalid reasons', () => {
                jest.spyOn(comp, 'computeInvalidReasons').mockReturnValue([
                    { translateKey: 'testKey1', translateValues: 'testValue' },
                    { translateKey: 'testKey2', translateValues: 'testValue' },
                ]);
                expect(comp.invalidReasonsHTML()).toBe('testKey1   -   testKey2  ');
            });

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

                it('should put reason for negative score ', () => {
                    question.points = -1;
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionScore');
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
                    expect(invalidReasonsAnswerOptions.length).toBe(1);
                });

                it('should put reason exactly once if more than one answer option hint is too long', () => {
                    answerOption1.hint = 'f'.repeat(255 + 1);
                    answerOption2.hint = 'f'.repeat(255 + 1);
                    const invalidReasonsAnswerOptions = comp
                        .computeInvalidReasons()
                        .filter((reason) => reason.translateKey === 'artemisApp.quizExercise.invalidReasons.answerHintLength');
                    expect(invalidReasonsAnswerOptions.length).toBe(1);
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

                it('should put reason for no correct mappings ', () => {
                    question.correctMappings = [];
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionCorrectMapping');
                });

                it('should put reason for unsolvable ', () => {
                    jest.spyOn(dragAndDropQuestionUtil, 'solve').mockReturnValue([]);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionUnsolvable');
                });

                it('should put reason for misleading correct mappings ', () => {
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

                it('should put reason for no correct mappings ', () => {
                    question.correctMappings = [];
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionCorrectMapping');
                });

                it('should put reason for misleading correct mappings ', () => {
                    jest.spyOn(shortAnswerQuestionUtil, 'validateNoMisleadingShortAnswerMapping').mockReturnValue(false);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.misleadingCorrectMapping');
                });

                it('should put reason when every spot has a solution ', () => {
                    jest.spyOn(shortAnswerQuestionUtil, 'everySpotHasASolution').mockReturnValue(false);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.shortAnswerQuestionEverySpotHasASolution');
                });

                it('should put reason when every mapped solution has a spot ', () => {
                    jest.spyOn(shortAnswerQuestionUtil, 'everyMappedSolutionHasASpot').mockReturnValue(false);
                    filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.shortAnswerQuestionEveryMappedSolutionHasASpot');
                });

                it('should put reason when there is an empty solution ', () => {
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

                it('should put reason and flag as invalid if a spot is invalid', () => {
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
