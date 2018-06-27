import { Injectable } from '@angular/core';
import * as moment from 'moment';
import { Router } from '@angular/router';
import { QuizExerciseService } from '../../entities/quiz-exercise';

@Injectable()
export class QuizStatisticUtil {
    constructor(private router: Router,
                private quizExerciseService: QuizExerciseService) {}

    /**
     * got to the Template with the previous Statistic
     * if first QuestionStatistic -> go to the Quiz-Statistic
     *
     * @param quizExercise: the quizExercise with all statistics
     * @param question: the question of the current statistic
     */
    previousStatistic(quizExercise, question) {
        // find position in quiz
        const index = quizExercise.questions.findIndex(function(quiz) {
            return quiz.id === question.id;
        });
        // go to quiz-Statistic if the position = 0
        if (index === 0) {
            this.router.navigateByUrl(`/quiz/${quizExercise.id}/quiz-statistic`);
        } else {
            // go to previous Question-statistic
            if (quizExercise.questions[index - 1].type === 'multiple-choice') {
                this.router.navigateByUrl(`/quiz/${quizExercise.id}/multiple-choice-question-statistic/` +
                    `${quizExercise.questions[index - 1].id}`);
            }
            if (quizExercise.questions[index - 1].type === 'drag-and-drop') {
                this.router.navigateByUrl(`/quiz/${quizExercise.id}/drag-and-drop-question-statistic/` +
                    `${quizExercise.questions[index - 1].id}`);
            }
        }
    }

    /**
     * got to the Template with the next Statistic
     * if last QuestionStatistic -> go to the Quiz-Point-Statistic
     *
     * @param quizExercise: the quizExercise with all statistics
     * @param question: the question of the current statistic
     */
    nextStatistic(quizExercise, question) {
        // find position in quiz
        const index = quizExercise.questions.findIndex(function(quiz) {
            return quiz.id === question.id;
        });
        // go to quiz-Statistic if the position = last position
        if (index === quizExercise.questions.length - 1) {
            this.router.navigateByUrl(`/quiz/${quizExercise.id}/quiz-point-statistic`);
        } else {
            // go to next Question-statistic
            if (quizExercise.questions[index + 1].type === 'multiple-choice') {
                this.router.navigateByUrl(`/quiz/${quizExercise.id}/multiple-choice-question-statistic/` +
                    `${quizExercise.questions[index + 1].id}`);
            }
            if (quizExercise.questions[index + 1].type === 'drag-and-drop') {
                this.router.navigateByUrl(`/quiz/${quizExercise.id}/drag-and-drop-question-statistic/` +
                    `${quizExercise.questions[index + 1].id}`);
            }
        }
    }

    /**
     * release of revoke all statistics of the quizExercise
     *
     * @param quizExercise: the quiz, which statistics should be revoked or released
     * @param {boolean} released: true to release, false to revoke
     */
    releaseStatistics(released, quizExercise) {
        if (released === quizExercise.quizPointStatistic.released) {
            return;
        }
        // check if it's allowed to release the statistics, if not send alert and do nothing
        if (released && this.releaseButtonDisabled(quizExercise)) {
            alert('Quiz hasn\'t ended yet!');
            return;
        }
        if (quizExercise.id) {
            quizExercise.quizPointStatistic.released = released;
            if (released) {
                this.quizExerciseService.releaseStatistics(quizExercise.id).subscribe(
                    () => {
                    },
                    () => {
                        alert('Error!');
                    });
            } else {
                this.quizExerciseService.revokeStatistics(quizExercise.id).subscribe();
            }
        }
    }

    /**
     * check if it's allowed to release the Statistic (allowed if the quiz is finished)
     *
     * @param quizExercise the quizExercise,
     *                      which will be checked if the release of the statistic is allowed
     * @returns {boolean} true if it's allowed, false if not
     */
    releaseButtonDisabled(quizExercise) {
        if (quizExercise) {
            return (!quizExercise.isPlannedToStart
                || moment().isBefore(quizExercise.dueDate));
        } else {
            return true;
        }
    }
}
