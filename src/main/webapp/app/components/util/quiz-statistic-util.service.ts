import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { QuizExercise, QuizExerciseService } from '../../entities/quiz-exercise';
import { QuizQuestion, QuizQuestionType } from '../../entities/quiz-question';

@Injectable({ providedIn: 'root' })
export class QuizStatisticUtil {
    constructor(private router: Router, private quizExerciseService: QuizExerciseService) {}

    /**
     * go to the Template with the previous QuizStatistic
     * if first QuizQuestionStatistic -> go to the Quiz-QuizStatistic
     *
     * @param quizExercise: the quizExercise with all statistics
     * @param question: the question of the current statistic
     */
    previousStatistic(quizExercise: QuizExercise, question: QuizQuestion) {
        // find position in quiz
        const index = quizExercise.quizQuestions.findIndex(function(quiz) {
            return quiz.id === question.id;
        });
        // go to quiz-QuizStatistic if the position = 0
        if (index === 0) {
            this.router.navigateByUrl('/quiz/' + quizExercise.id + '/quiz-statistic');
        } else {
            // go to previous Question-statistic
            const previousQuestion = quizExercise.quizQuestions[index - 1];
            if (previousQuestion.type === QuizQuestionType.MULTIPLE_CHOICE) {
                this.router.navigateByUrl('/quiz/' + quizExercise.id + '/multiple-choice-question-statistic/' + previousQuestion.id);
            } else if (previousQuestion.type === QuizQuestionType.DRAG_AND_DROP) {
                this.router.navigateByUrl('/quiz/' + quizExercise.id + '/drag-and-drop-question-statistic/' + previousQuestion.id);
            } else if (previousQuestion.type === QuizQuestionType.SHORT_ANSWER) {
                this.router.navigateByUrl('/quiz/' + quizExercise.id + '/short-answer-question-statistic/' + previousQuestion.id);
            }
        }
    }

    /**
     * go to the Template with the next QuizStatistic
     * if last QuizQuestionStatistic -> go to the Quiz-Point-QuizStatistic
     *
     * @param quizExercise: the quizExercise with all statistics
     * @param question: the question of the current statistic
     */
    nextStatistic(quizExercise: QuizExercise, question: QuizQuestion) {
        // find position in quiz
        const index = quizExercise.quizQuestions.findIndex(function(quiz) {
            return quiz.id === question.id;
        });
        // go to quiz-QuizStatistic if the position = last position
        if (index === quizExercise.quizQuestions.length - 1) {
            this.router.navigateByUrl('/quiz/' + quizExercise.id + '/quiz-point-statistic');
        } else {
            // go to next Question-statistic
            const nextQuestion = quizExercise.quizQuestions[index + 1];
            if (nextQuestion.type === QuizQuestionType.MULTIPLE_CHOICE) {
                this.router.navigateByUrl('/quiz/' + quizExercise.id + '/multiple-choice-question-statistic/' + nextQuestion.id);
            } else if (nextQuestion.type === QuizQuestionType.DRAG_AND_DROP) {
                this.router.navigateByUrl('/quiz/' + quizExercise.id + '/drag-and-drop-question-statistic/' + nextQuestion.id);
            } else if (nextQuestion.type === QuizQuestionType.SHORT_ANSWER) {
                this.router.navigateByUrl('/quiz/' + quizExercise.id + '/short-answer-question-statistic/' + nextQuestion.id);
            }
        }
    }

}
