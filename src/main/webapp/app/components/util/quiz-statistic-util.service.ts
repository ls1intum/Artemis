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
            }
        }
    }

    /**
     * release of revoke all statistics of the quizExercise
     *
     * @param quizExercise: the quiz, which statistics should be revoked or released
     * @param {boolean} released: true to release, false to revoke
     */
    releaseStatistics(released: boolean, quizExercise: QuizExercise) {
        if (released === quizExercise.quizPointStatistic.released) {
            return;
        }
        // check if it's allowed to release the statistics, if not send alert and do nothing
        if (released && this.releaseButtonDisabled(quizExercise)) {
            alert("Quiz hasn't ended yet!");
            return;
        }
        if (quizExercise.id) {
            quizExercise.quizPointStatistic.released = released;
            if (released) {
                this.quizExerciseService.releaseStatistics(quizExercise.id).subscribe(
                    () => {},
                    () => {
                        alert('Error!');
                    }
                );
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
    releaseButtonDisabled(quizExercise: QuizExercise) {
        if (quizExercise) {
            return !quizExercise.isPlannedToStart || moment().isBefore(quizExercise.dueDate);
        } else {
            return true;
        }
    }
}
