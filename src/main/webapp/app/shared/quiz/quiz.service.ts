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
                quizExercise.quizQuestions?.shuffle();
            }

            // shuffle answerOptions / dragItems within questions
            quizExercise.quizQuestions.forEach((question) => {
                if (question.randomizeOrder) {
                    if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                        (question as MultipleChoiceQuestion).answerOptions?.shuffle();
                    } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                        (question as DragAndDropQuestion).dragItems?.shuffle();
                    } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                    } else {
                        captureException(new Error('Unknown question type: ' + question));
                    }
                }
            }, this);
        }
    }
}
