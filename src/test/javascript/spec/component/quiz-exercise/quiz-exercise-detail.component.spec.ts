import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick, waitForAsync } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { QuizExerciseDetailComponent } from 'app/exercises/quiz/manage/quiz-exercise-detail.component';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import * as chai from 'chai';
import * as moment from 'moment';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { SinonStub, stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('QuizExercise Management Detail Component', () => {
    let comp: QuizExerciseDetailComponent;
    let courseManagementService: CourseManagementService;
    let quizExerciseService: QuizExerciseService;
    let fileUploaderService: FileUploaderService;
    let fixture: ComponentFixture<QuizExerciseDetailComponent>;

    const course: Course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    const mcQuestion = new MultipleChoiceQuestion();
    const answerOption = new AnswerOption();

    const resetQuizExercise = () => {
        quizExercise.id = 456;
        quizExercise.title = 'test';
        quizExercise.duration = 600;
        answerOption.isCorrect = true;
        mcQuestion.title = 'test';
        mcQuestion.answerOptions = [answerOption];
        quizExercise.quizQuestions = [mcQuestion];
    };

    resetQuizExercise();

    const route = ({ snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: quizExercise.id }) } } as any) as ActivatedRoute;

    const createValidMCQuestion = () => {
        const question = new MultipleChoiceQuestion();
        question.title = 'test';
        const answerOption1 = new AnswerOption();
        answerOption1.text = 'wrong answer';
        answerOption1.explanation = 'wrong explanation';
        answerOption1.hint = 'wrong hint';
        answerOption1.isCorrect = false;
        const answerOption2 = new AnswerOption();
        answerOption1.text = 'right answer';
        answerOption1.explanation = 'right explanation';
        answerOption1.isCorrect = true;
        question.answerOptions = [answerOption1, answerOption2];
        return { question, answerOption1, answerOption2 };
    };

    const createValidDnDQuestion = () => {
        const question = new DragAndDropQuestion();
        question.title = 'test';
        const dragItem1 = new DragItem();
        dragItem1.text = 'dragItem 1';
        const dragItem2 = new DragItem();
        dragItem2.text = 'dragItem 1';
        question.dragItems = [dragItem1, dragItem2];
        const dropLocation1 = new DropLocation();
        dropLocation1.posX = 50;
        dropLocation1.posY = 60;
        dropLocation1.width = 70;
        dropLocation1.height = 80;
        question.dropLocations = [dropLocation1];
        const dragAndDropMapping = new DragAndDropMapping(dragItem1, dropLocation1);
        question.correctMappings = [dragAndDropMapping];
        return { question, dragItem1, dragItem2, dropLocation1 };
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
        return { question, shortAnswerMapping1, shortAnswerMapping2, spot1, spot2, shortAnswerSolution1, shortAnswerSolution2 };
    };

    beforeEach(
        waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [QuizExerciseDetailComponent],
                providers: [
                    { provide: ActivatedRoute, useValue: route },
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: TranslateService, useClass: MockTranslateService },
                ],
            })
                .overrideTemplate(QuizExerciseDetailComponent, '')
                .compileComponents();
        }),
    );

    beforeEach(() => {
        fixture = TestBed.createComponent(QuizExerciseDetailComponent);
        comp = fixture.componentInstance;
        courseManagementService = fixture.debugElement.injector.get(CourseManagementService);
        quizExerciseService = fixture.debugElement.injector.get(QuizExerciseService);
        fileUploaderService = TestBed.inject(FileUploaderService);
    });

    describe('OnInit', () => {
        it('Should call courseExerciseService.find and quizExerciseService.find', () => {
            // GIVEN
            const quizExerciseServiceStub = stub(quizExerciseService, 'find');
            const courseManagementServiceStub = stub(courseManagementService, 'find');

            quizExerciseServiceStub.returns(
                of(
                    new HttpResponse<QuizExercise>({ body: quizExercise }),
                ),
            );
            courseManagementServiceStub.returns(
                of(
                    new HttpResponse<Course>({ body: course }),
                ),
            );

            // WHEN
            comp.course = course;
            comp.ngOnInit();

            // THEN
            expect(quizExerciseServiceStub).to.have.been.called;
            expect(courseManagementServiceStub).to.have.been.called;
        });
    });

    describe('onDurationChange', () => {
        // setup
        beforeEach(() => {
            comp.quizExercise = quizExercise;
        });

        it('Should update duration and quizExercise.duration with same values', () => {
            comp.duration = { minutes: 15, seconds: 30 };
            comp.onDurationChange();

            // compare duration with quizExercise.duration
            const durationAsSeconds = moment.duration(comp.duration).asSeconds();
            expect(durationAsSeconds).to.equal(comp.quizExercise.duration);
        });

        it('Should increase minutes when reaching 60 seconds', () => {
            comp.duration = { minutes: 0, seconds: 60 };
            comp.onDurationChange();

            expect(comp.duration.minutes).to.equal(1);
            expect(comp.duration.seconds).to.equal(0);
        });

        it('Should decrease minutes when reaching -1 seconds', () => {
            comp.duration = { minutes: 1, seconds: -1 };
            comp.onDurationChange();

            expect(comp.duration.minutes).to.equal(0);
            expect(comp.duration.seconds).to.equal(59);
        });
    });

    describe('add empty questions', () => {
        // setup
        beforeEach(() => {
            resetQuizExercise();
            comp.quizExercise = quizExercise;
        });

        it('should add empty MC question', () => {
            const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
            comp.addMultipleChoiceQuestion();

            expect(comp.quizExercise.quizQuestions).to.have.lengthOf(amountQuizQuestions + 1);
            expect(comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1].type).to.equal(QuizQuestionType.MULTIPLE_CHOICE);
        });

        it('should add empty DnD question', () => {
            const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
            comp.addDragAndDropQuestion();

            expect(comp.quizExercise.quizQuestions).to.have.lengthOf(amountQuizQuestions + 1);
            expect(comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1].type).to.equal(QuizQuestionType.DRAG_AND_DROP);
        });

        it('should add empty SA question', () => {
            const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
            comp.addShortAnswerQuestion();

            expect(comp.quizExercise.quizQuestions).to.have.lengthOf(amountQuizQuestions + 1);
            expect(comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1].type).to.equal(QuizQuestionType.SHORT_ANSWER);
        });

        afterAll(() => {
            quizExercise.quizQuestions = [mcQuestion];
        });
    });

    describe('delete questions', () => {
        const deleteQuestionAndExpect = () => {
            const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
            const questionToDelete = comp.quizExercise.quizQuestions![amountQuizQuestions - 1];
            comp.deleteQuestion(questionToDelete);
            expect(comp.quizExercise.quizQuestions).to.have.lengthOf(amountQuizQuestions - 1);
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

    describe('import questions', () => {
        const importQuestionAndExpectOneMoreQuestionInQuestions = (question: QuizQuestion, withTick: boolean) => {
            const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
            comp.verifyAndImportQuestions([question]);
            if (withTick) {
                tick();
            }
            expect(comp.quizExercise.quizQuestions).to.have.lengthOf(amountQuizQuestions + 1);
        };
        // setup
        beforeEach(() => {
            resetQuizExercise();
            comp.quizExercise = quizExercise;
        });

        it('should import MC question ', () => {
            const { question, answerOption1, answerOption2 } = createValidMCQuestion();
            importQuestionAndExpectOneMoreQuestionInQuestions(question, false);
            const lastAddedQuestion = comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1] as MultipleChoiceQuestion;
            expect(lastAddedQuestion.type).to.equal(QuizQuestionType.MULTIPLE_CHOICE);
            expect(lastAddedQuestion.answerOptions).to.have.lengthOf(2);
            expect(lastAddedQuestion.answerOptions![0]).to.deep.equal(answerOption1);
            expect(lastAddedQuestion.answerOptions![1]).to.deep.equal(answerOption2);
        });

        it('should import DnD question', fakeAsync(() => {
            const { question, dragItem1, dragItem2, dropLocation1 } = createValidDnDQuestion();

            // mock fileUploaderService
            spyOn(fileUploaderService, 'duplicateFile').and.returnValue(Promise.resolve({ path: 'test' }));
            importQuestionAndExpectOneMoreQuestionInQuestions(question, true);
            const lastAddedQuestion = comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1] as DragAndDropQuestion;
            expect(lastAddedQuestion.type).to.equal(QuizQuestionType.DRAG_AND_DROP);
            expect(lastAddedQuestion.correctMappings).to.have.lengthOf(1);
            expect(lastAddedQuestion.dragItems![0]).to.deep.equal(dragItem1);
            expect(lastAddedQuestion.dragItems![1]).to.deep.equal(dragItem2);
            expect(lastAddedQuestion.dropLocations![0]).to.deep.equal(dropLocation1);
        }));

        it('should import SA question', () => {
            const { question, shortAnswerMapping1, shortAnswerMapping2, spot1, spot2, shortAnswerSolution1, shortAnswerSolution2 } = createValidSAQuestion();
            importQuestionAndExpectOneMoreQuestionInQuestions(question, false);
            const lastAddedQuestion = comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1] as ShortAnswerQuestion;
            expect(lastAddedQuestion.type).to.equal(QuizQuestionType.SHORT_ANSWER);
            expect(lastAddedQuestion.correctMappings).to.have.lengthOf(2);
            expect(lastAddedQuestion.correctMappings![0]).to.deep.equal(shortAnswerMapping1);
            expect(lastAddedQuestion.correctMappings![1]).to.deep.equal(shortAnswerMapping2);
            expect(lastAddedQuestion.spots).to.have.lengthOf(2);
            expect(lastAddedQuestion.spots![0]).to.equal(spot1);
            expect(lastAddedQuestion.spots![1]).to.equal(spot2);
            expect(lastAddedQuestion.solutions).to.have.lengthOf(2);
            expect(lastAddedQuestion.solutions![0]).to.equal(shortAnswerSolution1);
            expect(lastAddedQuestion.solutions![1]).to.equal(shortAnswerSolution2);
        });
    });

    describe('quiz validity', () => {
        // setup

        const removeQuestionTitleAndExpectInvalidQuiz = (question: QuizQuestion) => {
            question.title = '';
            comp.quizExercise.quizQuestions = [question];
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(false);
        };

        const removeCorrectMappingsAndExpectInvalidQuiz = (question: DragAndDropQuestion | ShortAnswerQuestion) => {
            question.correctMappings = [];
            comp.quizExercise.quizQuestions = [question];
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(false);
        };

        beforeEach(() => {
            resetQuizExercise();
            comp.quizExercise = quizExercise;
        });

        it('should be valid with default test setting', () => {
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(true);
        });

        it('should not be valid without a quiz title', () => {
            quizExercise.title = '';
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(false);
        });

        it('should be valid with valid MC question', () => {
            const { question } = createValidMCQuestion();
            comp.quizExercise.quizQuestions = [question];
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(true);
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
            expect(comp.quizIsValid).to.equal(false);
        });

        it('should be valid with valid DnD question', () => {
            const { question } = createValidDnDQuestion();
            comp.quizExercise.quizQuestions = [question];
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(true);
        });

        it('should not be valid if DnD question has no title', () => {
            const { question } = createValidDnDQuestion();
            removeQuestionTitleAndExpectInvalidQuiz(question);
        });

        it('should not be valid if DnD question has no correct mapping', () => {
            const { question } = createValidDnDQuestion();
            removeCorrectMappingsAndExpectInvalidQuiz(question);
        });

        it('should be valid with valid SA question', () => {
            const { question } = createValidSAQuestion();
            comp.quizExercise.quizQuestions = [question];
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(true);
        });

        it('should not be valid if SA question has no title', () => {
            const { question } = createValidSAQuestion();
            removeQuestionTitleAndExpectInvalidQuiz(question);
        });

        it('should not be valid if SA question has no correct mapping', () => {
            const { question } = createValidSAQuestion();
            removeCorrectMappingsAndExpectInvalidQuiz(question);
        });
    });

    describe('saving', () => {
        let quizExerciseServiceCreateStub: SinonStub;
        let quizExerciseServiceUpdateStub: SinonStub;

        const saveQuizWithPendingChangesCache = () => {
            comp.cacheValidation();
            comp.pendingChangesCache = true;
            comp.save();
        };

        beforeEach(() => {
            resetQuizExercise();
            comp.quizExercise = quizExercise;
            quizExerciseServiceCreateStub = stub(quizExerciseService, 'create');
            quizExerciseServiceCreateStub.returns(
                of(
                    new HttpResponse<QuizExercise>({ body: quizExercise }),
                ),
            );
            quizExerciseServiceUpdateStub = stub(quizExerciseService, 'update');
            quizExerciseServiceUpdateStub.returns(
                of(
                    new HttpResponse<QuizExercise>({ body: quizExercise }),
                ),
            );
        });

        it('should call create if valid and quiz exercise no id', () => {
            comp.quizExercise.id = undefined;
            saveQuizWithPendingChangesCache();
            expect(quizExerciseServiceCreateStub).to.have.been.called;
            expect(quizExerciseServiceUpdateStub).to.not.have.been.called;
        });

        it('should update if valid and quiz exercise has id', () => {
            saveQuizWithPendingChangesCache();
            expect(quizExerciseServiceCreateStub).to.not.have.been.called;
            expect(quizExerciseServiceUpdateStub).to.have.been.called;
        });

        it('should not save if not valid', () => {
            saveQuizWithPendingChangesCache();
            expect(quizExerciseServiceCreateStub).to.not.have.been.called;
            expect(quizExerciseServiceUpdateStub).to.have.been.called;
        });
    });
});
