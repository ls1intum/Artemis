import { Injectable } from '@angular/core';
import { InitializationState } from 'app/entities/participation/participation.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { captureException } from '@sentry/angular';

@Injectable({ providedIn: 'root' })
export class ArtemisQuizService {
    /**
     * Randomize the order of the questions
     * (and answerOptions or dragItems within each question)
     * if randomizeOrder is true
     *
     * @param quizQuestions {object} the quizQuestions to be randomized
     * @param randomizeQuestionOrder {object} the flag whether or not to randomize the quiz questions
     */
    randomizeOrder(quizQuestions: QuizQuestion[] | undefined, randomizeQuestionOrder: boolean | undefined) {
        if (quizQuestions) {
            // shuffle questions
            if (randomizeQuestionOrder) {
                quizQuestions?.shuffle();
            }

            // shuffle answerOptions / dragItems within questions
            quizQuestions.forEach((question) => {
                if (question.randomizeOrder) {
                    if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                        (question as MultipleChoiceQuestion).answerOptions?.shuffle();
                    } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                        (question as DragAndDropQuestion).dragItems?.shuffle();
                    } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                        // nothing to do here
                    } else {
                        captureException(new Error('Unknown question type: ' + question));
                    }
                }
            }, this);
        }
    }

    static isUninitialized(quizExercise: QuizExercise): boolean {
        return ArtemisQuizService.notEndedSubmittedOrFinished(quizExercise) && ArtemisQuizService.startedQuizBatch(quizExercise);
    }

    static notStarted(quizExercise: QuizExercise): boolean {
        return ArtemisQuizService.notEndedSubmittedOrFinished(quizExercise) && !ArtemisQuizService.startedQuizBatch(quizExercise);
    }

    private static notEndedSubmittedOrFinished(quizExercise: QuizExercise): boolean {
        return (
            !quizExercise.quizEnded &&
            (!quizExercise.studentParticipations?.[0]?.initializationState ||
                ![InitializationState.INITIALIZED, InitializationState.FINISHED].includes(quizExercise.studentParticipations[0].initializationState))
        );
    }

    private static startedQuizBatch(quizExercise: QuizExercise): boolean {
        return !!quizExercise.quizBatches?.some((batch) => batch.started);
    }
}
