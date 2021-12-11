import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

/**
 * calculate the maximal  possible Score for the quiz
 * @param quizExercise the exercise the score should be computed
 * @return (int): sum over the Scores of all questions
 */
export function calculateMaxScore(quizExercise: QuizExercise) {
    let result = 0;

    if (quizExercise.quizQuestions) {
        quizExercise.quizQuestions.forEach(function (question) {
            result = result + question.points!;
        });
    } else {
        result = quizExercise.maxPoints!;
    }
    return result;
}
