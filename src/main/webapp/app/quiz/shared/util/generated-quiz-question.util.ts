import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { BaseEntityWithTempId, DropLocation } from 'app/quiz/shared/entities/drop-location.model';
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
import { SubmittedAnswerBeforeEvaluation } from 'app/openapi/model/submitted-answer-before-evaluation';
import { SubmittedAnswerFromStudent } from 'app/openapi/model/submitted-answer-from-student';
import { QuizQuestionWithoutSolution } from 'app/openapi/model/quiz-question-without-solution';
import type { DragAndDropMapping as GeneratedDragAndDropMapping } from 'app/openapi/model/drag-and-drop-mapping';
import type { ShortAnswerMapping as GeneratedShortAnswerMapping } from 'app/openapi/model/short-answer-mapping';

/**
 * Bridges the generated quiz models and the quiz class graph.
 *
 * The generated models are plain interfaces, while components and question templates expect class instances: they
 * read prototype behaviour and rely on the client-side `tempID` that identifies an item before it has a server id.
 * Each response is therefore converted once, where it is received: `QuizExerciseService` converts the exercise
 * retrieval and authoring responses, and the components that call another generated quiz API directly (participation,
 * submission, batch, lifecycle, training) convert theirs. Code past that point works only on class instances.
 */

/** The generated model omits a nested object entirely when it is empty, so an absent list maps to an absent list. */
function hydrateEach<T extends object, S extends object>(create: () => T, sources: S[] | undefined): (T & S)[] | undefined {
    return sources?.map((source) => hydrate(create(), source));
}

/**
 * Hydrates a drag item, drop location, spot or solution. Their constructors assign a random `tempID`, which identifies
 * a part only until the server stores it. A stored part keeps its server id instead: with a random `tempID`, two loads of
 * the same question differ, and the re-evaluation warning reports unchanged mappings as changed.
 */
function toQuestionPart<T extends BaseEntityWithTempId, S extends { id?: number }>(part: T, source: S): T & S {
    const questionPart = hydrate(part, source);
    if (questionPart.id !== undefined) {
        delete questionPart.tempID;
    }
    return questionPart;
}

function toDragAndDropMapping(mapping: GeneratedDragAndDropMapping): DragAndDropMapping {
    const dragItem = mapping.dragItem ? toQuestionPart(new DragItem(), mapping.dragItem) : undefined;
    const dropLocation = mapping.dropLocation ? toQuestionPart(new DropLocation(), mapping.dropLocation) : undefined;
    return hydrate(new DragAndDropMapping(dragItem, dropLocation), { id: mapping.id, invalid: mapping.invalid });
}

function toShortAnswerMapping(mapping: GeneratedShortAnswerMapping): ShortAnswerMapping {
    const spot = mapping.spot ? toQuestionPart(new ShortAnswerSpot(), mapping.spot) : undefined;
    const solution = mapping.solution ? toQuestionPart(new ShortAnswerSolution(), mapping.solution) : undefined;
    return hydrate(new ShortAnswerMapping(spot, solution), { id: mapping.id, invalid: mapping.invalid });
}

/**
 * Converts a generated question into the matching {@link QuizQuestion} subclass.
 *
 * Both the solution-bearing and the solution-free variant are accepted: they differ only in the fields the server
 * withholds while a quiz is running, and the class graph is the same either way.
 *
 * @param question the generated question, discriminated on its `type` property
 * @returns a class instance carrying the same data
 */
export function toQuizQuestion(question: QuizQuestionWithSolution | QuizQuestionWithoutSolution): QuizQuestion {
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
            dragAndDropQuestion.dropLocations = question.dropLocations?.map((dropLocation) => toQuestionPart(new DropLocation(), dropLocation));
            dragAndDropQuestion.dragItems = question.dragItems?.map((dragItem) => toQuestionPart(new DragItem(), dragItem));
            dragAndDropQuestion.correctMappings = 'correctMappings' in question ? question.correctMappings?.map(toDragAndDropMapping) : undefined;
            return dragAndDropQuestion;
        }
        case 'short-answer': {
            const shortAnswerQuestion: ShortAnswerQuestion = hydrate(new ShortAnswerQuestion(), question);
            shortAnswerQuestion.spots = question.spots?.map((spot) => toQuestionPart(new ShortAnswerSpot(), spot));
            shortAnswerQuestion.solutions = question.solutions?.map((solution) => toQuestionPart(new ShortAnswerSolution(), solution));
            shortAnswerQuestion.correctMappings = 'correctMappings' in question ? question.correctMappings?.map(toShortAnswerMapping) : undefined;
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
 * Converts a submitted answer into the generated request model of the practice and preview endpoints.
 *
 * These endpoints take a narrower payload than the live one: plain ids instead of nested objects.
 *
 * @param submittedAnswer the answer the student assembled in the UI
 * @returns the generated request model for the practice and preview submission endpoints
 * @throws Error if the answer carries no recognised question type
 */
export function toSubmittedAnswerFromStudent(submittedAnswer: SubmittedAnswer): SubmittedAnswerFromStudent {
    const questionId = submittedAnswer.quizQuestion!.id!;
    switch (submittedAnswer.type) {
        case QuizQuestionType.MULTIPLE_CHOICE:
            return {
                type: 'multiple-choice',
                questionId,
                selectedOptions: (submittedAnswer as MultipleChoiceSubmittedAnswer).selectedOptions?.map((option) => option.id!) ?? [],
            };
        case QuizQuestionType.DRAG_AND_DROP:
            return {
                type: 'drag-and-drop',
                questionId,
                mappings:
                    (submittedAnswer as DragAndDropSubmittedAnswer).mappings?.flatMap((mapping) => {
                        const dragItemId = mapping.dragItem?.id;
                        const dropLocationId = mapping.dropLocation?.id;
                        return dragItemId !== undefined && dropLocationId !== undefined ? [{ dragItemId, dropLocationId }] : [];
                    }) ?? [],
            };
        case QuizQuestionType.SHORT_ANSWER:
            return {
                type: 'short-answer',
                questionId,
                submittedTexts:
                    (submittedAnswer as ShortAnswerSubmittedAnswer).submittedTexts?.flatMap((submittedText) => {
                        const text = submittedText.text;
                        const spotId = submittedText.spot?.id;
                        // Unanswered spots are omitted: the practice and preview payloads require submitted texts to be nonblank.
                        return text?.trim() && spotId !== undefined ? [{ text, spotId }] : [];
                    }) ?? [],
            };
        default:
            throw new Error('Unknown submitted answer type: ' + submittedAnswer.type);
    }
}

/**
 * Converts a submitted answer the server returned into the matching {@link SubmittedAnswer} subclass, so the question
 * components can render the stored selection with the same objects they rendered the student's input with.
 *
 * Answers returned before evaluation carry no score and a solution-free question; answers returned after evaluation
 * carry both. The class graph is the same, so one mapper serves the live, practice and preview flows.
 *
 * @param answer the generated answer, discriminated on its `type` property
 * @returns a class instance carrying the same data
 */
export function toSubmittedAnswer(answer: SubmittedAnswerAfterEvaluation | SubmittedAnswerBeforeEvaluation): SubmittedAnswer {
    // The scored variants add this field; the pre-evaluation variants omit it entirely.
    const scalars = { id: answer.id, scoreInPoints: 'scoreInPoints' in answer ? answer.scoreInPoints : undefined };
    // Call sites look the answer up by its question id, so the link has to survive the conversion.
    const quizQuestion = answer.quizQuestion ? toQuizQuestion(answer.quizQuestion) : undefined;
    switch (answer.type) {
        case 'multiple-choice': {
            const multipleChoiceAnswer = hydrate(new MultipleChoiceSubmittedAnswer(), scalars);
            multipleChoiceAnswer.quizQuestion = quizQuestion;
            multipleChoiceAnswer.selectedOptions = hydrateEach(() => new AnswerOption(), answer.selectedOptions);
            return multipleChoiceAnswer;
        }
        case 'drag-and-drop': {
            const dragAndDropAnswer = hydrate(new DragAndDropSubmittedAnswer(), scalars);
            dragAndDropAnswer.quizQuestion = quizQuestion;
            dragAndDropAnswer.mappings = answer.mappings?.map(toDragAndDropMapping);
            return dragAndDropAnswer;
        }
        case 'short-answer': {
            const shortAnswerAnswer = hydrate(new ShortAnswerSubmittedAnswer(), scalars);
            shortAnswerAnswer.quizQuestion = quizQuestion;
            shortAnswerAnswer.submittedTexts = answer.submittedTexts?.map((submittedText) => {
                const submittedTextInstance = hydrate(new ShortAnswerSubmittedText(), submittedText);
                submittedTextInstance.spot = submittedText.spot ? toQuestionPart(new ShortAnswerSpot(), submittedText.spot) : undefined;
                return submittedTextInstance;
            });
            return shortAnswerAnswer;
        }
    }
}
