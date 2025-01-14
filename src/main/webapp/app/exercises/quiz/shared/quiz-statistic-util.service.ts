import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { getCourseId } from 'app/entities/exercise.model';

@Injectable({ providedIn: 'root' })
export class QuizStatisticUtil {
    private router = inject(Router);

    /**
     * Gets the URL to the quiz exercise detail/edit view
     * which is used as basis for all quiz related subroutes.
     *
     * @param quizExercise the exercise to get the URL for
     * @returns string the URL to the quiz depending on if it is in an exam or not
     */
    getBaseUrlForQuizExercise(quizExercise: QuizExercise): string {
        const courseId = getCourseId(quizExercise);

        // Test if we're a course exercise
        if (!quizExercise.exerciseGroup) {
            return `/course-management/${courseId}/quiz-exercises/${quizExercise.id}`;
        }

        // Otherwise, we are in the exam mode
        const examId = quizExercise.exerciseGroup!.exam!.id!;
        const groupId = quizExercise.exerciseGroup!.id!;
        return `/course-management/${courseId}/exams/${examId}/exercise-groups/${groupId}/quiz-exercises/${quizExercise.id}`;
    }

    /**
     * go to the Template with the previous QuizStatistic
     * if first QuizQuestionStatistic -> go to the quiz-statistic
     *
     * @param quizExercise: the quizExercise with all statistics
     * @param question: the question of the current statistic
     */
    previousStatistic(quizExercise: QuizExercise, question: QuizQuestion) {
        const baseUrl = this.getBaseUrlForQuizExercise(quizExercise);

        // find position in quiz
        const index = quizExercise.quizQuestions!.findIndex(function (quiz) {
            return quiz.id === question.id;
        });
        // go to quiz-statistic if the position = 0
        if (index === 0) {
            this.router.navigateByUrl(baseUrl + '/quiz-point-statistic');
        } else {
            // go to previous Question-statistic
            this.navigateToStatisticOf(quizExercise, quizExercise.quizQuestions![index - 1]);
        }
    }

    /**
     * go to the Template with the next QuizStatistic
     * if last QuizQuestionStatistic -> go to the Quiz-point-statistic
     *
     * @param quizExercise: the quizExercise with all statistics
     * @param question: the question of the current statistic
     */
    nextStatistic(quizExercise: QuizExercise, question: QuizQuestion) {
        const baseUrl = this.getBaseUrlForQuizExercise(quizExercise);

        // find position in quiz
        const index = quizExercise.quizQuestions!.findIndex(function (quiz) {
            return quiz.id === question.id;
        });
        // go to quiz-statistic if the position = last position
        if (index === quizExercise.quizQuestions!.length - 1) {
            this.router.navigateByUrl(baseUrl + '/quiz-point-statistic');
        } else {
            // go to next Question-statistic
            this.navigateToStatisticOf(quizExercise, quizExercise.quizQuestions![index + 1]);
        }
    }

    /**
     * Uses the router to navigate to the statistics page depending on the question type
     * @param quizExercise Quiz exercise to which the quiz question belongs
     * @param question Question for which to navigate to the statistics
     */
    navigateToStatisticOf(quizExercise: QuizExercise, question: QuizQuestion) {
        const baseUrl = this.getBaseUrlForQuizExercise(quizExercise);

        if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
            this.router.navigateByUrl(baseUrl + `/mc-question-statistic/${question.id}`);
        } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
            this.router.navigateByUrl(baseUrl + `/dnd-question-statistic/${question.id}`);
        } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
            this.router.navigateByUrl(baseUrl + `/sa-question-statistic/${question.id}`);
        }
    }
}
