import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';

/**
 * calculate the maximal  possible Score for the quiz
 * @param quizQuestions array of questions contained by the quiz
 * @return (int): sum over the Scores of all questions
 */
export function calculateMaxScore(quizQuestions?: QuizQuestion[]) {
    let result = 0;

    if (quizQuestions) {
        quizQuestions.forEach(function (question) {
            result = result + question.points!;
        });
    } else {
        result = this.quizExercise.maxPoints!;
    }
    return result;
}
