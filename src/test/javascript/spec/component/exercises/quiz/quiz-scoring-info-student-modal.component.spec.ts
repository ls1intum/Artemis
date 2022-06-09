import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { QuizScoringInfoStudentModalComponent } from 'app/exercises/quiz/shared/questions/quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Result } from 'app/entities/result.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { SubmittedAnswer } from 'app/entities/quiz/submitted-answer.model';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';

describe('Quiz Scoring Info Student Modal Component', () => {
    let fixture: ComponentFixture<QuizScoringInfoStudentModalComponent>;
    let comp: QuizScoringInfoStudentModalComponent;
    let modalService: NgbModal;
    let translateService: TranslateService;
    let translateSpy: jest.SpyInstance;
    const translationBasePath = 'artemisApp.quizExercise.explanationText.';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [QuizScoringInfoStudentModalComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizScoringInfoStudentModalComponent);
                comp = fixture.componentInstance;
                modalService = TestBed.inject(NgbModal);
                translateService = TestBed.inject(TranslateService);

                translateSpy = jest.spyOn(translateService, 'instant');
                comp.question = {} as ShortAnswerQuestion;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should check for singular point singular score', () => {
        comp.question.points = 1;
        comp.score = 1;
        comp.ngAfterViewInit();
        expect(translateSpy).toHaveBeenCalledTimes(2);
        expect(translateSpy).toHaveBeenNthCalledWith(1, translationBasePath + 'point');
        expect(translateSpy).toHaveBeenNthCalledWith(2, translationBasePath + 'point');
    });

    it('should check for plural points and scores', () => {
        comp.question.points = 2;
        comp.score = 2;
        comp.ngAfterViewInit();
        expect(translateSpy).toHaveBeenCalledTimes(2);
        expect(translateSpy).toHaveBeenNthCalledWith(1, translationBasePath + 'points');
        expect(translateSpy).toHaveBeenNthCalledWith(2, translationBasePath + 'points');
    });

    it('should open modal', () => {
        const content: any = {} as HTMLElement;
        const openModalSpy = jest.spyOn(modalService, 'open');

        comp.open(content);

        expect(openModalSpy).toHaveBeenCalledOnce();
        expect(openModalSpy).toHaveBeenCalledWith(content, { size: 'lg' });
    });

    it('check count for drag and drop exercise with singular values', () => {
        comp.question = new DragAndDropQuestion();
        comp.correctlyMappedDragAndDropItems = 1;
        comp.incorrectlyMappedDragAndDropItems = 1;
        comp.ngAfterViewInit();

        expect(comp.differenceDragAndDrop).toBe(0);
        expect(translateSpy).toHaveBeenCalledTimes(4);
        expect(translateSpy).toHaveBeenNthCalledWith(3, translationBasePath + 'item');
        expect(translateSpy).toHaveBeenNthCalledWith(4, translationBasePath + 'item');
    });

    it('check count for drag and drop exercise with plural values', () => {
        comp.question = new DragAndDropQuestion();
        comp.correctlyMappedDragAndDropItems = 5;
        comp.incorrectlyMappedDragAndDropItems = 2;
        comp.ngAfterViewInit();

        expect(comp.differenceDragAndDrop).toBe(3);
        expect(translateSpy).toHaveBeenCalledTimes(4);
        expect(translateSpy).toHaveBeenNthCalledWith(3, translationBasePath + 'items');
        expect(translateSpy).toHaveBeenNthCalledWith(4, translationBasePath + 'items');
    });

    it('check count for short answer exercise with singular values', () => {
        const question = new ShortAnswerQuestion();
        question.spots = [new ShortAnswerSpot(), new ShortAnswerSpot()];
        comp.question = question;

        const shortAnswerText1 = new ShortAnswerSubmittedText();
        shortAnswerText1.isCorrect = true;
        const shortAnswerText2 = new ShortAnswerSubmittedText();
        shortAnswerText2.isCorrect = false;
        comp.shortAnswerText = [shortAnswerText1, shortAnswerText2];

        comp.ngAfterViewInit();

        expect(comp.shortAnswerSpots).toBe(2);
        expect(comp.shortAnswerCorrectAnswers).toBe(1);
        expect(comp.shortAnswerWrongAnswers).toBe(1);
        expect(comp.differenceShortAnswer).toBe(0);
        expect(translateSpy).toHaveBeenCalledTimes(4);
        expect(translateSpy).toHaveBeenNthCalledWith(3, translationBasePath + 'textgap');
        expect(translateSpy).toHaveBeenNthCalledWith(4, translationBasePath + 'textgap');
    });

    it('check count for short answer exercise with plural values', () => {
        const question = new ShortAnswerQuestion();
        question.spots = [new ShortAnswerSpot(), new ShortAnswerSpot(), new ShortAnswerSpot(), new ShortAnswerSpot()];
        comp.question = question;

        const shortAnswerText1 = new ShortAnswerSubmittedText();
        shortAnswerText1.isCorrect = true;
        const shortAnswerText2 = new ShortAnswerSubmittedText();
        shortAnswerText2.isCorrect = true;
        const shortAnswerText3 = new ShortAnswerSubmittedText();
        shortAnswerText3.isCorrect = false;
        const shortAnswerText4 = new ShortAnswerSubmittedText();
        shortAnswerText4.isCorrect = false;
        comp.shortAnswerText = [shortAnswerText1, shortAnswerText2, shortAnswerText3, shortAnswerText4];

        comp.ngAfterViewInit();

        expect(comp.shortAnswerSpots).toBe(4);
        expect(comp.shortAnswerCorrectAnswers).toBe(2);
        expect(comp.shortAnswerWrongAnswers).toBe(2);
        expect(comp.differenceShortAnswer).toBe(0);
        expect(translateSpy).toHaveBeenCalledTimes(4);
        expect(translateSpy).toHaveBeenNthCalledWith(3, translationBasePath + 'textgaps');
        expect(translateSpy).toHaveBeenNthCalledWith(4, translationBasePath + 'textgaps');
    });

    describe('Check values for multiple choice exercise', () => {
        let question: MultipleChoiceQuestion;
        let submittedAnswers: SubmittedAnswer[];
        beforeEach(() => {
            // multiple choice question with 2 answers
            // submission with one correct answer and one wrong answer
            question = new MultipleChoiceQuestion();
            const correctAnswer1 = new AnswerOption();
            correctAnswer1.id = 1;
            correctAnswer1.isCorrect = true;
            const wrongAnswer1 = new AnswerOption();
            wrongAnswer1.id = 2;
            wrongAnswer1.isCorrect = false;
            question.answerOptions = [correctAnswer1, wrongAnswer1];
            comp.question = question;

            const submittedExercise = new QuizExercise(undefined, undefined);
            submittedExercise.quizQuestions = [question];
            comp.submittedQuizExercise = submittedExercise;

            const submittedResult = new Result();
            comp.multipleChoiceSubmittedResult = submittedResult;

            const submission = new QuizSubmission();
            submittedResult.submission = submission;

            const correctSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
            correctSubmittedAnswer.quizQuestion = question;
            const correctSelectedOption = new AnswerOption();
            correctSelectedOption.id = 1;
            correctSelectedOption.isCorrect = true;
            correctSubmittedAnswer.selectedOptions = [correctSelectedOption];

            const wrongSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
            wrongSubmittedAnswer.quizQuestion = question;
            const wrongSelectedOption = new AnswerOption();
            wrongSelectedOption.id = 2;
            wrongSelectedOption.isCorrect = false;
            wrongSubmittedAnswer.selectedOptions = [wrongSelectedOption];

            submittedAnswers = [correctSubmittedAnswer, wrongSubmittedAnswer];
            submission.submittedAnswers = submittedAnswers;
            submittedResult.submission = submission;
        });

        it('check count for multiple choice exercise with singular values', () => {
            comp.ngAfterViewInit();

            expect(comp.multipleChoiceAnswerOptions).toBe(2);
            expect(comp.multipleChoiceCorrectAnswerCorrectlyChosen).toBe(1);
            expect(comp.multipleChoiceWrongAnswerChosen).toBe(1);
            expect(comp.forgottenMultipleChoiceRightAnswers).toBe(0);
            expect(comp.inTotalSelectedRightOptions).toBe(1);
            expect(comp.forgottenMultipleChoiceRightAnswers).toBe(0);
            expect(comp.inTotalSelectedWrongOptions).toBe(1);
            expect(translateSpy).toHaveBeenCalledTimes(4);
            expect(translateSpy).toHaveBeenNthCalledWith(3, translationBasePath + 'option');
            expect(translateSpy).toHaveBeenNthCalledWith(4, translationBasePath + 'option');
        });

        it('check count for multiple choice exercise with plural values', () => {
            // add one wrong and one correct answer to the exercise
            const correctAnswer2 = new AnswerOption();
            correctAnswer2.id = 3;
            correctAnswer2.isCorrect = true;
            const wrongAnswer2 = new AnswerOption();
            wrongAnswer2.id = 4;
            wrongAnswer2.isCorrect = false;

            question.answerOptions!.push(correctAnswer2, wrongAnswer2);

            const correctSubmittedAnswer2 = new MultipleChoiceSubmittedAnswer();
            correctSubmittedAnswer2.quizQuestion = question;
            const correctSelectedOption2 = new AnswerOption();
            correctSelectedOption2.id = 3;
            correctSelectedOption2.isCorrect = true;
            correctSubmittedAnswer2.selectedOptions = [correctSelectedOption2];
            const wrongSubmittedAnswer2 = new MultipleChoiceSubmittedAnswer();
            wrongSubmittedAnswer2.quizQuestion = question;
            const wrongSelectedOption2 = new AnswerOption();
            wrongSelectedOption2.id = 4;
            wrongSelectedOption2.isCorrect = false;
            wrongSubmittedAnswer2.selectedOptions = [wrongSelectedOption2];

            submittedAnswers.push(correctSubmittedAnswer2, wrongSubmittedAnswer2);

            comp.ngAfterViewInit();

            expect(comp.multipleChoiceAnswerOptions).toBe(4);
            expect(comp.multipleChoiceCorrectAnswerCorrectlyChosen).toBe(2);
            expect(comp.multipleChoiceWrongAnswerChosen).toBe(2);
            expect(comp.forgottenMultipleChoiceRightAnswers).toBe(0);
            expect(comp.inTotalSelectedRightOptions).toBe(2);
            expect(comp.forgottenMultipleChoiceRightAnswers).toBe(0);
            expect(comp.inTotalSelectedWrongOptions).toBe(2);
            expect(translateSpy).toHaveBeenCalledTimes(4);
            expect(translateSpy).toHaveBeenNthCalledWith(3, translationBasePath + 'options');
            expect(translateSpy).toHaveBeenNthCalledWith(4, translationBasePath + 'options');
        });
    });
});
