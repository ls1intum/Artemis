import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';
import { ShortAnswerMapping } from 'app/quiz/shared/entities/short-answer-mapping.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { DragAndDropSubmittedAnswer } from 'app/quiz/shared/entities/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { QuizQuestionWithSolution } from 'app/openapi/model/quiz-question-with-solution';
import { SubmittedAnswerFromLiveClient } from 'app/openapi/model/submitted-answer-from-live-client';
import { SubmittedAnswerAfterEvaluation } from 'app/openapi/model/submitted-answer-after-evaluation';
import type { DragAndDropMapping as GeneratedDragAndDropMapping } from 'app/openapi/model/drag-and-drop-mapping';
import type { ShortAnswerMapping as GeneratedShortAnswerMapping } from 'app/openapi/model/short-answer-mapping';

/**
 * Bridges the generated quiz models and the quiz class graph.
 *
 * The generated models are plain interfaces, while components and question templates expect class instances: they
 * read prototype behaviour and rely on the client-side `tempID` that identifies an item before it has a server id.
 * Conversion therefore happens once, at the service boundary, rather than being spread over call sites.
 */

/** The generated model omits a nested object entirely when it is empty, so an absent list maps to an absent list. */
function hydrateEach<T extends object, S extends object>(create: () => T, sources: S[] | undefined): (T & S)[] | undefined {
    return sources?.map((source) => hydrate(create(), source));
}

function toDragAndDropMapping(mapping: GeneratedDragAndDropMapping): DragAndDropMapping {
    const dragItem = mapping.dragItem ? hydrate(new DragItem(), mapping.dragItem) : undefined;
    const dropLocation = mapping.dropLocation ? hydrate(new DropLocation(), mapping.dropLocation) : undefined;
    return hydrate(new DragAndDropMapping(dragItem, dropLocation), { id: mapping.id, invalid: mapping.invalid });
}

function toShortAnswerMapping(mapping: GeneratedShortAnswerMapping): ShortAnswerMapping {
    const spot = mapping.spot ? hydrate(new ShortAnswerSpot(), mapping.spot) : undefined;
    const solution = mapping.solution ? hydrate(new ShortAnswerSolution(), mapping.solution) : undefined;
    return hydrate(new ShortAnswerMapping(spot, solution), { id: mapping.id, invalid: mapping.invalid });
}

/**
 * Converts a generated question into the matching {@link QuizQuestion} subclass.
 *
 * @param question the generated question, discriminated on its `type` property
 * @returns a class instance carrying the same data
 */
export function toQuizQuestion(question: QuizQuestionWithSolution): QuizQuestion {
    switch (question.type) {
        case 'multiple-choice': {
            // The class types `type` as the enum and the generated model as a string literal, so the intersection
            // hydrate() returns collapses to never. Annotating the target keeps the class view of the data.
            const multipleChoiceQuestion: MultipleChoiceQuestion = hydrate(new MultipleChoiceQuestion(), question);
            multipleChoiceQuestion.answerOptions = hydrateEach(() => new AnswerOption(), question.answerOptions);
            return multipleChoiceQuestion;
        }
        case 'drag-and-drop': {
            const dragAndDropQuestion: DragAndDropQuestion = hydrate(new DragAndDropQuestion(), question);
            dragAndDropQuestion.dropLocations = hydrateEach(() => new DropLocation(), question.dropLocations);
            dragAndDropQuestion.dragItems = hydrateEach(() => new DragItem(), question.dragItems);
            dragAndDropQuestion.correctMappings = question.correctMappings?.map(toDragAndDropMapping);
            return dragAndDropQuestion;
        }
        case 'short-answer': {
            const shortAnswerQuestion: ShortAnswerQuestion = hydrate(new ShortAnswerQuestion(), question);
            shortAnswerQuestion.spots = hydrateEach(() => new ShortAnswerSpot(), question.spots);
            shortAnswerQuestion.solutions = hydrateEach(() => new ShortAnswerSolution(), question.solutions);
            shortAnswerQuestion.correctMappings = question.correctMappings?.map(toShortAnswerMapping);
            return shortAnswerQuestion;
        }
    }
}

/**
 * Converts a submitted answer into the generated request model.
 *
 * Only the ids of the referenced question components travel to the server; it resolves them against the stored
 * question, so sending the nested objects would be wasted payload.
 *
 * @param submittedAnswer the answer the student assembled in the UI
 * @returns the generated request model for the live-client submission endpoints
 * @throws Error if the answer carries no recognised question type
 */
export function toSubmittedAnswerFromLiveClient(submittedAnswer: SubmittedAnswer): SubmittedAnswerFromLiveClient {
    const quizQuestion = { id: submittedAnswer.quizQuestion?.id };
    switch (submittedAnswer.type) {
        case QuizQuestionType.MULTIPLE_CHOICE:
            return {
                type: 'multiple-choice',
                quizQuestion,
                selectedOptions: (submittedAnswer as MultipleChoiceSubmittedAnswer).selectedOptions?.map((option) => ({ id: option.id })),
            };
        case QuizQuestionType.DRAG_AND_DROP:
            return {
                type: 'drag-and-drop',
                quizQuestion,
                mappings: (submittedAnswer as DragAndDropSubmittedAnswer).mappings?.map((mapping) => ({
                    dragItem: { id: mapping.dragItem?.id },
                    dropLocation: { id: mapping.dropLocation?.id },
                })),
            };
        case QuizQuestionType.SHORT_ANSWER:
            return {
                type: 'short-answer',
                quizQuestion,
                submittedTexts: (submittedAnswer as ShortAnswerSubmittedAnswer).submittedTexts?.map((submittedText) => ({
                    text: submittedText.text,
                    spot: { id: submittedText.spot?.id },
                })),
            };
        default:
            throw new Error('Unknown submitted answer type: ' + submittedAnswer.type);
    }
}

/**
 * Converts an evaluated answer into the matching {@link SubmittedAnswer} subclass, so the question components can
 * render the server's verdict with the same objects they rendered the student's selection with.
 *
 * @param evaluatedAnswer the generated answer returned after evaluation
 * @returns a class instance carrying the same data
 */
export function toSubmittedAnswer(evaluatedAnswer: SubmittedAnswerAfterEvaluation): SubmittedAnswer {
    switch (evaluatedAnswer.type) {
        case 'multiple-choice': {
            const multipleChoiceAnswer = hydrate(new MultipleChoiceSubmittedAnswer(), { id: evaluatedAnswer.id, scoreInPoints: evaluatedAnswer.scoreInPoints });
            multipleChoiceAnswer.selectedOptions = hydrateEach(() => new AnswerOption(), evaluatedAnswer.selectedOptions);
            return multipleChoiceAnswer;
        }
        case 'drag-and-drop': {
            const dragAndDropAnswer = hydrate(new DragAndDropSubmittedAnswer(), { id: evaluatedAnswer.id, scoreInPoints: evaluatedAnswer.scoreInPoints });
            dragAndDropAnswer.mappings = evaluatedAnswer.mappings?.map(toDragAndDropMapping);
            return dragAndDropAnswer;
        }
        case 'short-answer': {
            const shortAnswerAnswer = hydrate(new ShortAnswerSubmittedAnswer(), { id: evaluatedAnswer.id, scoreInPoints: evaluatedAnswer.scoreInPoints });
            shortAnswerAnswer.submittedTexts = evaluatedAnswer.submittedTexts?.map((submittedText) => {
                const submittedTextInstance = hydrate(new ShortAnswerSubmittedText(), submittedText);
                submittedTextInstance.spot = submittedText.spot ? hydrate(new ShortAnswerSpot(), submittedText.spot) : undefined;
                return submittedTextInstance;
            });
            return shortAnswerAnswer;
        }
    }
}
