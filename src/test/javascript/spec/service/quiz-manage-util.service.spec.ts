import { isQuizEditable } from 'app/exercises/quiz/shared/quiz-manage-util.service';
import { QuizBatch, QuizExercise, QuizMode, QuizStatus } from 'app/entities/quiz/quiz-exercise.model';

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
});
