import { Injectable } from '@angular/core';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { captureException } from '@sentry/browser';

@Injectable({ providedIn: 'root' })
export class ArtemisQuizService {
    /**
     * Randomize the order of the questions
     * (and answerOptions or dragItems within each question)
     * if randomizeOrder is true
     *
     * @param quizExercise {object} the quizExercise to randomize elements in
     */
    randomizeOrder(quizExercise: QuizExercise) {
        if (quizExercise.quizQuestions) {
            // shuffle questions
            if (quizExercise.randomizeQuestionOrder) {
                this.shuffle(quizExercise.quizQuestions);
            }

            // shuffle answerOptions / dragItems within questions
            quizExercise.quizQuestions.forEach((question) => {
                if (question.randomizeOrder) {
                    if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                        this.shuffle((question as MultipleChoiceQuestion).answerOptions!);
                    } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                        this.shuffle((question as DragAndDropQuestion).dragItems!);
                    } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                    } else {
                        captureException(new Error('Unknown question type: ' + question));
                    }
                }
            }, this);
        }
    }

    /**
     * Shuffles array in place.
     * @param {Array} items An array containing the items.
     */
    shuffle<T>(items: T[]) {
        for (let i = items.length - 1; i > 0; i--) {
            const pickedIndex = Math.floor(Math.random() * (i + 1));
            const picked = items[pickedIndex];
            items[pickedIndex] = items[i];
            items[i] = picked;
        }
    }
}
