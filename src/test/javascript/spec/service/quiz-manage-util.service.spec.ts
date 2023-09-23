import { isQuizEditable, isQuizQuestionValid } from 'app/exercises/quiz/shared/quiz-manage-util.service';
import { QuizBatch, QuizExercise, QuizMode, QuizStatus } from 'app/entities/quiz/quiz-exercise.model';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';

describe('QuizManageUtil', () => {
    let quizExercise: QuizExercise;

    describe('isQuizEditable', () => {
        beforeEach(() => {
            quizExercise = new QuizExercise(undefined, undefined);
            quizExercise.id = 1;
            quizExercise.title = 'test';
            quizExercise.duration = 600;
            quizExercise.quizMode = QuizMode.SYNCHRONIZED;
            quizExercise.status = QuizStatus.VISIBLE;
            quizExercise.isAtLeastEditor = true;
            quizExercise.quizEnded = false;
        });

        it('should return true if new quiz', () => {
            quizExercise.id = undefined;
            expect(isQuizEditable(quizExercise)).toBeTrue();
        });

        it('should return true if existing quiz is synchronized, not active and not over', () => {
            expect(isQuizEditable(quizExercise)).toBeTrue();
        });

        it('should return true if existing quiz is batched, no batch exists, not active, at least editor and not over', () => {
            quizExercise.quizMode = QuizMode.BATCHED;
            quizExercise.quizBatches = undefined;
            expect(isQuizEditable(quizExercise)).toBeTrue();
        });

        it('should return false if existing quiz is batched, batch exists, not active, at least editor and not over', () => {
            quizExercise.quizMode = QuizMode.BATCHED;
            quizExercise.quizBatches = [new QuizBatch()];
            expect(isQuizEditable(quizExercise)).toBeFalse();
        });

        it('should return false if existing quiz is synchronized, active, at least editor, and not over', () => {
            quizExercise.quizMode = QuizMode.SYNCHRONIZED;
            quizExercise.status = QuizStatus.ACTIVE;
            expect(isQuizEditable(quizExercise)).toBeFalse();
        });

        it('should return false if existing quiz is synchronized, not active, at least editor, and over', () => {
            quizExercise.quizMode = QuizMode.SYNCHRONIZED;
            quizExercise.quizEnded = true;
            expect(isQuizEditable(quizExercise)).toBeFalse();
        });

        it('should return false if existing quiz is synchronized, not active, not editor and not over', () => {
            quizExercise.quizMode = QuizMode.SYNCHRONIZED;
            quizExercise.isAtLeastEditor = false;
            expect(isQuizEditable(quizExercise)).toBeFalse();
        });
    });

    describe('isQuizQuestionValid', () => {
        const dragAndDropQuestionUtil = new DragAndDropQuestionUtil();
        const shortAnswerQuestionUtil = new ShortAnswerQuestionUtil();
        const multipleChoiceQuestion = new MultipleChoiceQuestion();

        it('should return false if points is undefined', () => {
            multipleChoiceQuestion.points = undefined;
            expect(isQuizQuestionValid(multipleChoiceQuestion, dragAndDropQuestionUtil, shortAnswerQuestionUtil)).toBeFalse();
        });

        it('should return false if points < 1', () => {
            multipleChoiceQuestion.points = -1;
            expect(isQuizQuestionValid(multipleChoiceQuestion, dragAndDropQuestionUtil, shortAnswerQuestionUtil)).toBeFalse();
        });

        it('should return false if points > 9999', () => {
            multipleChoiceQuestion.points = 10000;
            expect(isQuizQuestionValid(multipleChoiceQuestion, dragAndDropQuestionUtil, shortAnswerQuestionUtil)).toBeFalse();
        });

        it('should return true if question is valid', () => {
            multipleChoiceQuestion.points = 100;
            multipleChoiceQuestion.title = 'Title';
            const answerOption0 = new AnswerOption();
            const answerOption1 = new AnswerOption();
            answerOption0.isCorrect = true;
            multipleChoiceQuestion.answerOptions = [answerOption0, answerOption1];
            expect(isQuizQuestionValid(multipleChoiceQuestion, dragAndDropQuestionUtil, shortAnswerQuestionUtil)).toBeTrue();
        });
    });
});
