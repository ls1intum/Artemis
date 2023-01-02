import { QuizExercise, QuizMode, QuizStatus } from 'app/entities/quiz/quiz-exercise.model';

/**
 * Check if quiz is editable
 * @param quizExercise the quiz exercise which will be checked
 * @return {boolean} true if the quiz is editable and false otherwise
 */
export function isQuizEditable(quizExercise: QuizExercise): boolean {
    if (quizExercise.id) {
        if (quizExercise.quizMode === QuizMode.BATCHED && quizExercise.quizBatches?.length) {
            return false;
        }
        return quizExercise.status !== QuizStatus.ACTIVE && quizExercise.isAtLeastEditor! && !quizExercise.quizEnded;
    }
    return true;
}
