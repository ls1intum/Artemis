import { QuizExercise, QuizMode, QuizStatus } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { captureException } from '@sentry/angular';
import { ValidationReason } from 'app/entities/exercise.model';
import {
    MAX_QUIZ_QUESTION_EXPLANATION_LENGTH_THRESHOLD,
    MAX_QUIZ_QUESTION_HINT_LENGTH_THRESHOLD,
    MAX_QUIZ_QUESTION_LENGTH_THRESHOLD,
    MAX_QUIZ_QUESTION_POINTS,
} from 'app/shared/constants/input.constants';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { CanBecomeInvalid, DropLocation } from 'app/entities/quiz/drop-location.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';

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

export function isQuizQuestionValid(question: QuizQuestion, dragAndDropQuestionUtil: DragAndDropQuestionUtil, shortAnswerQuestionUtil: ShortAnswerQuestionUtil) {
    if (question.points == undefined || question.points < 1 || question.points > MAX_QUIZ_QUESTION_POINTS) {
        return false;
    }
    if (question.explanation && question.explanation.length > MAX_QUIZ_QUESTION_EXPLANATION_LENGTH_THRESHOLD) {
        return false;
    }
    if (question.hint && question.hint.length > MAX_QUIZ_QUESTION_HINT_LENGTH_THRESHOLD) {
        return false;
    }
    switch (question.type) {
        case QuizQuestionType.MULTIPLE_CHOICE: {
            const mcQuestion = question as MultipleChoiceQuestion;
            const correctOptions = mcQuestion.answerOptions!.filter((answerOption) => answerOption.isCorrect).length;
            return (
                (mcQuestion.singleChoice ? correctOptions === 1 : correctOptions > 0) &&
                question.title &&
                question.title !== '' &&
                question.title.length < MAX_QUIZ_QUESTION_LENGTH_THRESHOLD &&
                mcQuestion.answerOptions!.every(
                    (answerOption) =>
                        answerOption.isCorrect !== undefined &&
                        (!answerOption.explanation || answerOption.explanation.length <= MAX_QUIZ_QUESTION_EXPLANATION_LENGTH_THRESHOLD) &&
                        (!answerOption.hint || answerOption.hint.length <= MAX_QUIZ_QUESTION_HINT_LENGTH_THRESHOLD),
                )
            );
        }
        case QuizQuestionType.DRAG_AND_DROP: {
            const dndQuestion = question as DragAndDropQuestion;
            return (
                question.title &&
                question.title !== '' &&
                question.title.length < MAX_QUIZ_QUESTION_LENGTH_THRESHOLD &&
                dndQuestion.correctMappings &&
                dndQuestion.correctMappings.length > 0 &&
                dragAndDropQuestionUtil.solve(dndQuestion).length &&
                dragAndDropQuestionUtil.validateNoMisleadingCorrectMapping(dndQuestion)
            );
        }
        case QuizQuestionType.SHORT_ANSWER: {
            const shortAnswerQuestion = question as ShortAnswerQuestion;
            return (
                question.title &&
                question.title !== '' &&
                shortAnswerQuestion.correctMappings &&
                shortAnswerQuestion.correctMappings.length > 0 &&
                shortAnswerQuestionUtil.validateNoMisleadingShortAnswerMapping(shortAnswerQuestion) &&
                shortAnswerQuestionUtil.everySpotHasASolution(shortAnswerQuestion.correctMappings, shortAnswerQuestion.spots!) &&
                shortAnswerQuestionUtil.everyMappedSolutionHasASpot(shortAnswerQuestion.correctMappings) &&
                shortAnswerQuestion.solutions?.filter((solution) => solution.text!.trim() === '').length === 0 &&
                shortAnswerQuestion.solutions?.filter((solution) => solution.text!.trim().length >= MAX_QUIZ_QUESTION_LENGTH_THRESHOLD).length === 0 &&
                !shortAnswerQuestionUtil.hasMappingDuplicateValues(shortAnswerQuestion.correctMappings) &&
                shortAnswerQuestionUtil.atLeastAsManySolutionsAsSpots(shortAnswerQuestion)
            );
        }
        default: {
            captureException(new Error('Unknown question type: ' + question));
            return question.title && question.title !== '';
        }
    }
}

/**
 * Compute the invalid reasons of the given QuizQuestion and add them to the given invalidReasons list.
 *
 * @param invalidReasons the invalid reasons of the given quiz question
 * @param question the QuizQuestion from which the invalid reasons to be computed
 * @param index the index of the QuizQuestion
 * @param dragAndDropQuestionUtil the utility service of drag and drop quiz question
 * @param shortAnswerQuestionUtil the utility service of short answer quiz question
 */
export function computeQuizQuestionInvalidReason(
    invalidReasons: ValidationReason[],
    question: QuizQuestion,
    index: number,
    dragAndDropQuestionUtil: DragAndDropQuestionUtil,
    shortAnswerQuestionUtil: ShortAnswerQuestionUtil,
) {
    if (!question.title || question.title === '') {
        invalidReasons.push({
            translateKey: 'artemisApp.quizExercise.invalidReasons.questionTitle',
            translateValues: { index: index + 1 },
        });
    }
    if (question.points == undefined) {
        invalidReasons.push({
            translateKey: 'artemisApp.quizExercise.invalidReasons.questionScore',
            translateValues: { index: index + 1 },
        });
    } else if (question.points < 1 || question.points > MAX_QUIZ_QUESTION_POINTS) {
        invalidReasons.push({
            translateKey: 'artemisApp.quizExercise.invalidReasons.questionScoreInvalid',
            translateValues: { index: index + 1 },
        });
    }
    if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
        const mcQuestion = question as MultipleChoiceQuestion;
        const correctOptions = mcQuestion.answerOptions!.filter((answerOption) => answerOption.isCorrect).length;
        if (correctOptions === 0) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.questionCorrectAnswerOption',
                translateValues: { index: index + 1 },
            });
        }
        if (mcQuestion.singleChoice && correctOptions > 1) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.questionSingleChoiceCorrectAnswerOptions',
                translateValues: { index: index + 1 },
            });
        }
        if (!mcQuestion.answerOptions!.every((answerOption) => answerOption.explanation !== '')) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.explanationIsMissing',
                translateValues: { index: index + 1 },
            });
        }
        if (mcQuestion.answerOptions!.some((answerOption) => answerOption.explanation && answerOption.explanation.length > MAX_QUIZ_QUESTION_EXPLANATION_LENGTH_THRESHOLD)) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.answerExplanationLength',
                translateValues: { index: index + 1, threshold: MAX_QUIZ_QUESTION_EXPLANATION_LENGTH_THRESHOLD },
            });
        }
        if (mcQuestion.answerOptions!.some((answerOption) => answerOption.hint && answerOption.hint.length > MAX_QUIZ_QUESTION_HINT_LENGTH_THRESHOLD)) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.answerHintLength',
                translateValues: { index: index + 1, threshold: MAX_QUIZ_QUESTION_HINT_LENGTH_THRESHOLD },
            });
        }
        if (mcQuestion.answerOptions!.some((answerOption) => answerOption.isCorrect === undefined)) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.multipleChoiceQuestionAnswerOptionInvalid',
                translateValues: { index: index + 1, threshold: MAX_QUIZ_QUESTION_HINT_LENGTH_THRESHOLD },
            });
        }
    }
    if (question.title && question.title.length >= MAX_QUIZ_QUESTION_LENGTH_THRESHOLD) {
        invalidReasons.push({
            translateKey: 'artemisApp.quizExercise.invalidReasons.questionTitleLength',
            translateValues: { index: index + 1, threshold: MAX_QUIZ_QUESTION_LENGTH_THRESHOLD },
        });
    }
    if (question.explanation && question.explanation.length > MAX_QUIZ_QUESTION_EXPLANATION_LENGTH_THRESHOLD) {
        invalidReasons.push({
            translateKey: 'artemisApp.quizExercise.invalidReasons.questionExplanationLength',
            translateValues: { index: index + 1, threshold: MAX_QUIZ_QUESTION_EXPLANATION_LENGTH_THRESHOLD },
        });
    }
    if (question.hint && question.hint.length > MAX_QUIZ_QUESTION_HINT_LENGTH_THRESHOLD) {
        invalidReasons.push({
            translateKey: 'artemisApp.quizExercise.invalidReasons.questionHintLength',
            translateValues: { index: index + 1, threshold: MAX_QUIZ_QUESTION_HINT_LENGTH_THRESHOLD },
        });
    }

    if (question.type === QuizQuestionType.DRAG_AND_DROP) {
        const dndQuestion = question as DragAndDropQuestion;
        if (!dndQuestion.correctMappings || dndQuestion.correctMappings.length === 0) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.questionCorrectMapping',
                translateValues: { index: index + 1 },
            });
        } else if (dragAndDropQuestionUtil.solve(dndQuestion, []).length === 0) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.questionUnsolvable',
                translateValues: { index: index + 1 },
            });
        }
        if (!dragAndDropQuestionUtil.validateNoMisleadingCorrectMapping(dndQuestion)) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.misleadingCorrectMapping',
                translateValues: { index: index + 1 },
            });
        }
    }
    if (question.type === QuizQuestionType.SHORT_ANSWER) {
        const shortAnswerQuestion = question as ShortAnswerQuestion;
        if (!shortAnswerQuestion.correctMappings || shortAnswerQuestion.correctMappings.length === 0) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.questionCorrectMapping',
                translateValues: { index: index + 1 },
            });
        }
        if (!shortAnswerQuestionUtil.validateNoMisleadingShortAnswerMapping(shortAnswerQuestion)) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.misleadingCorrectMapping',
                translateValues: { index: index + 1 },
            });
        }
        if (!shortAnswerQuestionUtil.everySpotHasASolution(shortAnswerQuestion.correctMappings!, shortAnswerQuestion.spots!)) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionEverySpotHasASolution',
                translateValues: { index: index + 1 },
            });
        }
        if (!shortAnswerQuestionUtil.everyMappedSolutionHasASpot(shortAnswerQuestion.correctMappings!)) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionEveryMappedSolutionHasASpot',
                translateValues: { index: index + 1 },
            });
        }
        if (!(shortAnswerQuestion.solutions?.filter((solution) => solution.text!.trim() === '').length === 0)) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionSolutionHasNoValue',
                translateValues: { index: index + 1 },
            });
        }
        if (shortAnswerQuestion.solutions?.filter((solution) => solution.text!.trim().length >= MAX_QUIZ_QUESTION_LENGTH_THRESHOLD).length !== 0) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.quizAnswerOptionLength',
                translateValues: { index: index + 1, threshold: MAX_QUIZ_QUESTION_LENGTH_THRESHOLD },
            });
        }
        if (shortAnswerQuestionUtil.hasMappingDuplicateValues(shortAnswerQuestion.correctMappings!)) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionDuplicateMapping',
                translateValues: { index: index + 1 },
            });
        }
        if (!shortAnswerQuestionUtil.atLeastAsManySolutionsAsSpots(shortAnswerQuestion)) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionUnsolvable',
                translateValues: { index: index + 1 },
            });
        }
    }
}

type InvalidElement = AnswerOption | ShortAnswerSolution | ShortAnswerMapping | ShortAnswerSpot | DropLocation | DragItem | DragAndDropMapping;

export type InvalidFlaggedQuestions = {
    [id: number]: InvalidElement[];
};

/**
 * Compute the list of invalid flagged QuizQuestions from the given list of QuizQuestions.
 *
 * @param questions the list of QuizQuestion to be computed
 * @return the list of QuizQuestions which were marked invalid
 */
export function checkForInvalidFlaggedQuestions(questions: QuizQuestion[]): InvalidFlaggedQuestions {
    const invalidQuestions: InvalidFlaggedQuestions = {};
    questions.forEach((question) => {
        const invalidQuestion = question.invalid;
        const invalidElements: InvalidElement[] = [];
        if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
            pushToInvalidElements((<MultipleChoiceQuestion>question).answerOptions, invalidElements);
        } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
            pushToInvalidElements((<DragAndDropQuestion>question).dragItems, invalidElements);
            pushToInvalidElements((<DragAndDropQuestion>question).correctMappings, invalidElements);
            pushToInvalidElements((<DragAndDropQuestion>question).dropLocations, invalidElements);
        } else {
            pushToInvalidElements((<ShortAnswerQuestion>question).solutions, invalidElements);
            pushToInvalidElements((<ShortAnswerQuestion>question).correctMappings, invalidElements);
            pushToInvalidElements((<ShortAnswerQuestion>question).spots, invalidElements);
        }
        if (invalidQuestion || invalidElements.length !== 0) {
            invalidQuestions[question.id!] = invalidElements.length !== 0 ? invalidElements : [];
        }
    });
    return invalidQuestions;
}

/**
 * Helper function in order to prevent code duplication in computeInvalidReasons
 * Iterates over the array and pushes invalid elements to invalidElements
 * @param array the array containing elements that can be invalid
 * @param invalidElements the array all invalid elements are pushed to
 */
function pushToInvalidElements(array: CanBecomeInvalid[] | undefined, invalidElements: InvalidElement[]): void {
    if (array !== undefined) {
        array!.forEach(function (option: CanBecomeInvalid) {
            if (option.invalid) {
                invalidElements.push(option);
            }
        });
    }
}
