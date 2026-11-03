import { describe, expect, it } from 'vitest';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { toQuizExerciseReEvaluate } from 'app/quiz/shared/util/quiz-exercise-reevaluate-request.util';

describe('toQuizExerciseReEvaluate', () => {
    it.each([new MultipleChoiceQuestion(), new DragAndDropQuestion(), new ShortAnswerQuestion()])('should keep shuffling a $type question that lacks the setting', (question) => {
        question.id = 1;
        question.randomizeOrder = undefined as unknown as boolean;
        const exercise = new QuizExercise(undefined, undefined);
        exercise.quizQuestions = [question];

        expect(toQuizExerciseReEvaluate(exercise).quizQuestions[0].randomizeOrder).toBe(true);
    });

    it('should keep a question unshuffled when the instructor turned shuffling off', () => {
        const question = new MultipleChoiceQuestion();
        question.id = 1;
        question.randomizeOrder = false;
        const exercise = new QuizExercise(undefined, undefined);
        exercise.quizQuestions = [question];

        expect(toQuizExerciseReEvaluate(exercise).quizQuestions[0].randomizeOrder).toBe(false);
    });
});
