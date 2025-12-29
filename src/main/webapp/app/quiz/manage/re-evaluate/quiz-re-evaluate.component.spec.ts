import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { QuizReEvaluateComponent } from 'app/quiz/manage/re-evaluate/quiz-re-evaluate.component';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockRouter } from 'src/test/javascript/spec/helpers/mocks/mock-router';
import { MockProvider } from 'ng-mocks';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { SimpleChange } from '@angular/core';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'src/test/javascript/spec/helpers/mocks/service/mock-account.service';

describe('QuizExercise Re-evaluate Component', () => {
    setupTestBed({ zoneless: true });

    let comp: QuizReEvaluateComponent;
    let fixture: ComponentFixture<QuizReEvaluateComponent>;
    let quizService: QuizExerciseService;
    let quizServiceFindStub: ReturnType<typeof vi.spyOn>;

    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'MyQuiz';

    const route = { params: of({ exerciseId: 123 }) } as any as ActivatedRoute;

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

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(NgbModal),
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(NgbActiveModal),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(QuizReEvaluateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizReEvaluateComponent);
        comp = fixture.componentInstance;
        quizService = TestBed.inject(QuizExerciseService);
        const { question: quizQuestion1 } = createValidMCQuestion();
        const { question: quizQuestion2 } = createValidDnDQuestion();
        quizExercise.quizQuestions = [quizQuestion1, quizQuestion2];
        quizServiceFindStub = vi.spyOn(quizService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize quiz exercise', () => {
        comp.ngOnInit();
        expect(comp.isValidQuiz()).toBe(true);
        expect(comp.quizExercise).toEqual(quizExercise);
        expect(quizServiceFindStub).toHaveBeenCalledOnce();
    });

    it('should delete quiz question', () => {
        comp.ngOnInit();
        expect(comp.quizExercise.quizQuestions).toHaveLength(2);
        comp.deleteQuestion(comp.quizExercise.quizQuestions![0]);
        expect(comp.quizExercise.quizQuestions).toHaveLength(1);
    });

    it('should update and reset quiz questions', () => {
        comp.ngOnInit();
        comp.quizExercise.title = 'New Title';
        comp.quizExercise.quizQuestions![0].points = 5;
        // update question
        comp.onQuestionUpdated();
        expect(comp.quizExercise).toEqual(quizExercise);
        // reset title
        comp.resetQuizTitle();
        expect(comp.quizExercise.title).toBe(comp.savedEntity.title);
        // reset all
        comp.resetAll();
        expect(comp.quizExercise).toEqual(comp.savedEntity);
    });

    it('should have pending changes', () => {
        comp.ngOnInit();
        comp.quizExercise.quizQuestions![0].points = 5;
        expect(comp.pendingChanges()).toBe(true);
    });

    it('should move down the quiz question', () => {
        comp.ngOnInit();
        expect(comp.quizExercise.quizQuestions![0].type).toEqual(QuizQuestionType.MULTIPLE_CHOICE);
        comp.moveDown(comp.quizExercise.quizQuestions![0]);
        expect(comp.quizExercise.quizQuestions![1].type).toEqual(QuizQuestionType.MULTIPLE_CHOICE);
    });

    it('should move up the quiz question', () => {
        comp.ngOnInit();
        expect(comp.quizExercise.quizQuestions![1].type).toEqual(QuizQuestionType.DRAG_AND_DROP);
        comp.moveUp(comp.quizExercise.quizQuestions![1]);
        expect(comp.quizExercise.quizQuestions![0].type).toEqual(QuizQuestionType.DRAG_AND_DROP);
    });

    it('Updates quiz on changes', () => {
        const prepareEntitySpy = vi.spyOn(comp, 'prepareEntity');
        comp.ngOnInit();
        comp.ngOnChanges({
            quizExercise: { currentValue: quizExercise } as SimpleChange,
        });

        expect(prepareEntitySpy).toHaveBeenCalledTimes(2);
    });

    describe('Quiz question validation', () => {
        beforeEach(() => {
            comp.ngOnInit();
        });
        afterEach(() => {
            comp.resetAll();
            comp.onQuestionUpdated();
        });

        describe('Quiz mc question validation', () => {
            let mcQuestion: MultipleChoiceQuestion;
            let answerOption1: AnswerOption;
            beforeEach(() => {
                mcQuestion = comp.quizExercise.quizQuestions![0] as MultipleChoiceQuestion;
                answerOption1 = mcQuestion.answerOptions![0];
            });

            it('should be invalid if quiz hint is too long', () => {
                mcQuestion.hint = new Array(256 + 1).join('x');
                expect(mcQuestion.hint).toHaveLength(256);

                comp.onQuestionUpdated();

                expect(comp.quizIsValid).toBe(false);
            });

            it('should be invalid if quiz explanation is too long', () => {
                mcQuestion.explanation = new Array(501 + 1).join('x');
                expect(mcQuestion.explanation).toHaveLength(501);

                comp.onQuestionUpdated();

                expect(comp.quizIsValid).toBe(false);
            });

            it('should be invalid if answer option hint is too long', () => {
                answerOption1.hint = new Array(501 + 1).join('x');

                comp.onQuestionUpdated();

                expect(comp.quizIsValid).toBe(false);
            });

            it('should be invalid if answer option explanation is too long', () => {
                answerOption1.explanation = new Array(501 + 1).join('x');

                comp.onQuestionUpdated();

                expect(comp.quizIsValid).toBe(false);
            });
        });

        describe('Quiz dnd question validation', () => {
            let dndQuestion: DragAndDropQuestion;
            beforeEach(() => {
                dndQuestion = comp.quizExercise.quizQuestions![1] as DragAndDropQuestion;
            });

            it('should be invalid if question hint is invalid', () => {
                dndQuestion.hint = new Array(256 + 1).join('x');
                expect(dndQuestion.hint).toHaveLength(256);

                comp.onQuestionUpdated();

                expect(comp.quizIsValid).toBe(false);
            });

            it('should be invalid if question explanation is invalid', () => {
                dndQuestion.explanation = new Array(501 + 1).join('x');
                expect(dndQuestion.explanation).toHaveLength(501);

                comp.onQuestionUpdated();

                expect(comp.quizIsValid).toBe(false);
            });
        });
    });

    it('should change score calculation type', () => {
        comp.ngOnInit();
        expect(comp.quizExercise.includedInOverallScore).toEqual(IncludedInOverallScore.INCLUDED_COMPLETELY);
        comp.includedInOverallScoreChange(IncludedInOverallScore.INCLUDED_AS_BONUS);
        expect(comp.quizExercise.includedInOverallScore).toEqual(IncludedInOverallScore.INCLUDED_AS_BONUS);
    });

    it('should be valid when mc option is marked as invalid', () => {
        quizExercise.title = 'Test';
        quizExercise.duration = 100;
        const question = new MultipleChoiceQuestion();
        question.title = 'Test Question';
        question.singleChoice = false;
        const answerOption1 = new AnswerOption();
        answerOption1.isCorrect = true;
        const answerOption2 = new AnswerOption();
        answerOption2.isCorrect = true;
        const answerOption3 = new AnswerOption();
        answerOption3.isCorrect = false;
        question.answerOptions = [answerOption1, answerOption2, answerOption3];
        question.points = 100;
        quizExercise.quizQuestions = [question];
        comp.quizExercise = quizExercise;
        // @ts-ignore
        comp.invalidFlaggedQuestions = [question];
        expect(comp.isValidQuiz()).toBe(true);
    });
});
