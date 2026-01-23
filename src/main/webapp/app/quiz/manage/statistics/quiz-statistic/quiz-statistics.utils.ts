import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';

/**
 * calculate the maximal possible Score for the quiz
 * @param quizExercise the exercise the score should be computed
 * @return (int): sum over the Scores of all questions
 */
export function calculateMaxScore(quizExercise: QuizExercise) {
    const questions = quizExercise.quizQuestions ?? [];
    let result = 0;

    questions.forEach((question) => {
        result += question.points ?? 0;
    });

    return result;
}
