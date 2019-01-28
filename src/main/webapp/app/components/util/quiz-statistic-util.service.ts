import { Injectable } from '@angular/core';
import * as moment from 'moment';
import { Router } from '@angular/router';
import { QuizExercise, QuizExerciseService } from '../../entities/quiz-exercise';
import { Question, QuestionType } from '../../entities/question';

@Injectable({ providedIn: 'root' })
export class QuizStatisticUtil {
    constructor(private router: Router, private quizExerciseService: QuizExerciseService) {}

    /**
     * go to the Template with the previous Statistic
     * if first QuestionStatistic -> go to the Quiz-Statistic
     *
     * @param quizExercise: the quizExercise with all statistics
     * @param question: the question of the current statistic
     */
    previousStatistic(quizExercise: QuizExercise, question: Question) {
        // find position in quiz
        const index = quizExercise.questions.findIndex(function(quiz) {
            return quiz.id === question.id;
        });
        // go to quiz-Statistic if the position = 0
        if (index === 0) {
            this.router.navigateByUrl('/quiz/' + quizExercise.id + '/quiz-statistic');
        } else {
            // go to previous Question-statistic
            const previousQuestion = quizExercise.questions[index - 1];
            if (previousQuestion.type === QuestionType.MULTIPLE_CHOICE) {
                this.router.navigateByUrl('/quiz/' + quizExercise.id + '/multiple-choice-question-statistic/' + previousQuestion.id);
            } else if (previousQuestion.type === QuestionType.DRAG_AND_DROP) {
                this.router.navigateByUrl('/quiz/' + quizExercise.id + '/drag-and-drop-question-statistic/' + previousQuestion.id);
            } else if (previousQuestion.type === QuestionType.SHORT_ANSWER) {
                this.router.navigateByUrl('/quiz/' + quizExercise.id + '/short-answer-question-statistic/' + previousQuestion.id);
            }
        }
    }

    /**
     * go to the Template with the next Statistic
     * if last QuestionStatistic -> go to the Quiz-Point-Statistic
     *
     * @param quizExercise: the quizExercise with all statistics
     * @param question: the question of the current statistic
     */
    nextStatistic(quizExercise: QuizExercise, question: Question) {
        // find position in quiz
        const index = quizExercise.questions.findIndex(function(quiz) {
            return quiz.id === question.id;
        });
        // go to quiz-Statistic if the position = last position
        if (index === quizExercise.questions.length - 1) {
            this.router.navigateByUrl('/quiz/' + quizExercise.id + '/quiz-point-statistic');
        } else {
            // go to next Question-statistic
            const nextQuestion = quizExercise.questions[index + 1];
            if (nextQuestion.type === QuestionType.MULTIPLE_CHOICE) {
                this.router.navigateByUrl('/quiz/' + quizExercise.id + '/multiple-choice-question-statistic/' + nextQuestion.id);
            } else if (nextQuestion.type === QuestionType.DRAG_AND_DROP) {
                this.router.navigateByUrl('/quiz/' + quizExercise.id + '/drag-and-drop-question-statistic/' + nextQuestion.id);
            } else if (nextQuestion.type === QuestionType.SHORT_ANSWER) {
                this.router.navigateByUrl('/quiz/' + quizExercise.id + '/short-answer-question-statistic/' + nextQuestion.id);
            }
        }
    }

}
