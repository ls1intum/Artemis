import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { Course } from 'app/entities/course.model';
import { QuizReEvaluateComponent } from 'app/exercises/quiz/manage/re-evaluate/quiz-re-evaluate.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { ReEvaluateMultipleChoiceQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/multiple-choice-question/re-evaluate-multiple-choice-question.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ReEvaluateDragAndDropQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/drag-and-drop-question/re-evaluate-drag-and-drop-question.component';
import { ReEvaluateShortAnswerQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/short-answer-question/re-evaluate-short-answer-question.component';
import { NgModel } from '@angular/forms';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { Duration } from 'app/exercises/quiz/manage/quiz-exercise-interfaces';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { SimpleChange } from '@angular/core';
import { IncludedInOverallScorePickerComponent } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateValuesDirective } from '../../helpers/mocks/directive/mock-translate-values.directive';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';

describe('QuizExercise Re-evaluate Component', () => {
    let comp: QuizReEvaluateComponent;
    let fixture: ComponentFixture<QuizReEvaluateComponent>;
    let quizService: QuizExerciseService;
    let quizServiceFindStub: jest.SpyInstance;

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
            imports: [ArtemisTestModule],
            declarations: [
                NgModel,
                QuizReEvaluateComponent,
                MockComponent(ReEvaluateMultipleChoiceQuestionComponent),
                MockComponent(ReEvaluateDragAndDropQuestionComponent),
                MockComponent(ReEvaluateShortAnswerQuestionComponent),
                MockTranslateValuesDirective,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(IncludedInOverallScorePickerComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(NgbModal),
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
            ],
        })
            .overrideTemplate(QuizReEvaluateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizReEvaluateComponent);
        comp = fixture.componentInstance;
        quizService = fixture.debugElement.injector.get(QuizExerciseService);
        const { question: quizQuestion1 } = createValidMCQuestion();
        const { question: quizQuestion2 } = createValidDnDQuestion();
        quizExercise.quizQuestions = [quizQuestion1, quizQuestion2];
        quizServiceFindStub = jest.spyOn(quizService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Should initialize quiz exercise', () => {
        comp.ngOnInit();
        expect(comp.isValidQuiz()).toBeTrue();
        expect(comp.quizExercise).toEqual(quizExercise);
        expect(quizServiceFindStub).toHaveBeenCalledOnce();
    });

    it('Should create correct duration strings', () => {
        comp.duration = new Duration(1, 0);
        expect(comp.durationString()).toBe('1:00');

        comp.duration = new Duration(1, 9);
        expect(comp.durationString()).toBe('1:09');

        comp.duration = new Duration(1, 10);
        expect(comp.durationString()).toBe('1:10');
    });

    it('Should delete quiz question', () => {
        comp.ngOnInit();
        expect(comp.quizExercise.quizQuestions).toHaveLength(2);
        comp.deleteQuestion(comp.quizExercise.quizQuestions![0]);
        expect(comp.quizExercise.quizQuestions).toHaveLength(1);
    });

    it('Should update and reset quiz questions', () => {
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

    it('Should have pending changes', () => {
        comp.ngOnInit();
        comp.quizExercise.quizQuestions![0].points = 5;
        expect(comp.pendingChanges()).toBeTrue();
    });

    it('Should move down the quiz question', () => {
        comp.ngOnInit();
        expect(comp.quizExercise.quizQuestions![0].type).toEqual(QuizQuestionType.MULTIPLE_CHOICE);
        comp.moveDown(comp.quizExercise.quizQuestions![0]);
        expect(comp.quizExercise.quizQuestions![1].type).toEqual(QuizQuestionType.MULTIPLE_CHOICE);
    });

    it('Should move up the quiz question', () => {
        comp.ngOnInit();
        expect(comp.quizExercise.quizQuestions![1].type).toEqual(QuizQuestionType.DRAG_AND_DROP);
        comp.moveUp(comp.quizExercise.quizQuestions![1]);
        expect(comp.quizExercise.quizQuestions![0].type).toEqual(QuizQuestionType.DRAG_AND_DROP);
    });

    it('Updates quiz on changes', () => {
        const prepareEntitySpy = jest.spyOn(comp, 'prepareEntity');
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

            it('Should be invalid if quiz hint is too long', () => {
                mcQuestion.hint = new Array(256 + 1).join('x');
                expect(mcQuestion.hint).toHaveLength(256);

                comp.onQuestionUpdated();

                expect(comp.quizIsValid).toBeFalse();
            });

            it('Should be invalid if quiz explanation is too long', () => {
                mcQuestion.explanation = new Array(501 + 1).join('x');
                expect(mcQuestion.explanation).toHaveLength(501);

                comp.onQuestionUpdated();

                expect(comp.quizIsValid).toBeFalse();
            });

            it('Should be invalid if answer option hint is too long', () => {
                answerOption1.hint = new Array(501 + 1).join('x');

                comp.onQuestionUpdated();

                expect(comp.quizIsValid).toBeFalse();
            });

            it('Should be invalid if answer option explanation is too long', () => {
                answerOption1.explanation = new Array(501 + 1).join('x');

                comp.onQuestionUpdated();

                expect(comp.quizIsValid).toBeFalse();
            });
        });

        describe('Quiz dnd question validation', () => {
            let dndQuestion: DragAndDropQuestion;
            beforeEach(() => {
                dndQuestion = comp.quizExercise.quizQuestions![1] as DragAndDropQuestion;
            });

            it('Should be invalid if question hint is invalid', () => {
                dndQuestion.hint = new Array(256 + 1).join('x');
                expect(dndQuestion.hint).toHaveLength(256);

                comp.onQuestionUpdated();

                expect(comp.quizIsValid).toBeFalse();
            });

            it('Should be invalid if question explanation is invalid', () => {
                dndQuestion.explanation = new Array(501 + 1).join('x');
                expect(dndQuestion.explanation).toHaveLength(501);

                comp.onQuestionUpdated();

                expect(comp.quizIsValid).toBeFalse();
            });
        });
    });

    it('Should change score calculation type', () => {
        comp.ngOnInit();
        expect(comp.quizExercise.includedInOverallScore).toEqual(IncludedInOverallScore.INCLUDED_COMPLETELY);
        comp.includedInOverallScoreChange(IncludedInOverallScore.INCLUDED_AS_BONUS);
        expect(comp.quizExercise.includedInOverallScore).toEqual(IncludedInOverallScore.INCLUDED_AS_BONUS);
    });
});
