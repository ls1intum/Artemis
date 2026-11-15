import { type QuizQuestion, QuizQuestionType, ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import type { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import type { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import type { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import type { MultipleChoiceQuestionCreate } from 'app/openapi/model/multiple-choice-question-create';
import type { AnswerOptionCreate } from 'app/openapi/model/answer-option-create';
import type { DragAndDropQuestionCreate } from 'app/openapi/model/drag-and-drop-question-create';
import type { DropLocationCreate } from 'app/openapi/model/drop-location-create';
import type { DragItemCreate } from 'app/openapi/model/drag-item-create';
import type { DragAndDropMappingCreate } from 'app/openapi/model/drag-and-drop-mapping-create';
import type { ShortAnswerQuestionCreate } from 'app/openapi/model/short-answer-question-create';
import type { ShortAnswerSpotCreate } from 'app/openapi/model/short-answer-spot-create';
import type { ShortAnswerSolutionCreate } from 'app/openapi/model/short-answer-solution-create';
import type { ShortAnswerMappingCreate } from 'app/openapi/model/short-answer-mapping-create';

/*
 * The question part of the creation and the update requests. The two generated models differ only in the ids an
 * update carries for saved parts, so one mapping serves both. The Playwright suite loads this module in Node through
 * the creation request, so nothing here may import 'dayjs/esm', or a module that imports it, at runtime.
 */

/** A part of the creation model that may also carry the id of an already saved part, as the update model does. */
type WithId<T> = T & { id?: number };

type MultipleChoiceQuestionRequest = WithId<Omit<MultipleChoiceQuestionCreate, 'answerOptions'>> & {
    answerOptions: WithId<AnswerOptionCreate>[];
};

type DragAndDropQuestionRequest = WithId<Omit<DragAndDropQuestionCreate, 'dropLocations' | 'dragItems' | 'correctMappings'>> & {
    dropLocations: WithId<DropLocationCreate>[];
    dragItems: WithId<DragItemCreate>[];
    correctMappings: WithId<DragAndDropMappingCreate>[];
};

type ShortAnswerQuestionRequest = WithId<Omit<ShortAnswerQuestionCreate, 'spots' | 'solutions' | 'correctMappings'>> & {
    spots: WithId<ShortAnswerSpotCreate>[];
    solutions: WithId<ShortAnswerSolutionCreate>[];
    correctMappings: WithId<ShortAnswerMappingCreate>[];
};

/** A question in a shape that satisfies both the creation and the update request model. */
export type QuizQuestionRequest = MultipleChoiceQuestionRequest | DragAndDropQuestionRequest | ShortAnswerQuestionRequest;

/**
 * Selects the id a part sends. A creation request sends none, because the server assigns them; an update request
 * sends the id of every part that is already saved.
 */
export type PartIdSelector = (part: { id?: number }) => number | undefined;

/**
 * Identifies a part to the references within its question: by its id once it is saved, by its client-side temporary
 * id before. The server resolves the references the same way.
 *
 * @param part the referenced drop location, drag item, spot or solution
 * @returns the id that the part and every reference to it carry
 */
function toReferenceId(part: { id?: number; tempID?: number } | undefined): number {
    return part?.id ?? part?.tempID ?? 0;
}

function toMultipleChoiceQuestionRequest(question: MultipleChoiceQuestion, partId: PartIdSelector): MultipleChoiceQuestionRequest {
    return {
        type: 'multiple-choice',
        id: partId(question),
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        scoringType: question.scoringType ?? ScoringType.ALL_OR_NOTHING,
        randomizeOrder: question.randomizeOrder,
        answerOptions: (question.answerOptions ?? []).map((option) => ({
            id: partId(option),
            text: option.text ?? '',
            hint: option.hint,
            explanation: option.explanation,
            isCorrect: option.isCorrect ?? false,
        })),
        singleChoice: question.singleChoice ?? false,
    };
}

function toDragAndDropQuestionRequest(question: DragAndDropQuestion, partId: PartIdSelector): DragAndDropQuestionRequest {
    return {
        type: 'drag-and-drop',
        id: partId(question),
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        scoringType: question.scoringType ?? ScoringType.PROPORTIONAL_WITH_PENALTY,
        randomizeOrder: question.randomizeOrder,
        backgroundFilePath: question.backgroundFilePath,
        dropLocations: (question.dropLocations ?? []).map((dropLocation) => ({
            id: partId(dropLocation),
            tempID: toReferenceId(dropLocation),
            posX: dropLocation.posX ?? 0,
            posY: dropLocation.posY ?? 0,
            width: dropLocation.width ?? 0,
            height: dropLocation.height ?? 0,
        })),
        dragItems: (question.dragItems ?? []).map((dragItem) => ({
            id: partId(dragItem),
            tempID: toReferenceId(dragItem),
            text: dragItem.text,
            pictureFilePath: dragItem.pictureFilePath,
        })),
        correctMappings: (question.correctMappings ?? []).map((mapping) => ({
            id: partId(mapping),
            dragItemTempId: toReferenceId(mapping.dragItem),
            dropLocationTempId: toReferenceId(mapping.dropLocation),
        })),
    };
}

function toShortAnswerQuestionRequest(question: ShortAnswerQuestion, partId: PartIdSelector): ShortAnswerQuestionRequest {
    return {
        type: 'short-answer',
        id: partId(question),
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        // The server requires a scoring type. A question imported from a file may lack one, so fall back to the default
        // the editor assigns to a new short-answer question.
        scoringType: question.scoringType ?? ScoringType.PROPORTIONAL_WITHOUT_PENALTY,
        randomizeOrder: question.randomizeOrder,
        spots: (question.spots ?? []).map((spot) => ({
            id: partId(spot),
            tempID: toReferenceId(spot),
            spotNr: spot.spotNr ?? 0,
            width: spot.width ?? 0,
        })),
        solutions: (question.solutions ?? []).map((solution) => ({
            id: partId(solution),
            tempID: toReferenceId(solution),
            text: solution.text ?? '',
        })),
        correctMappings: (question.correctMappings ?? []).map((mapping) => ({
            id: partId(mapping),
            solutionTempId: toReferenceId(mapping.solution),
            spotTempId: toReferenceId(mapping.spot),
        })),
        similarityValue: question.similarityValue ?? 85,
        matchLetterCase: question.matchLetterCase ?? false,
    };
}

/**
 * Converts a question into the question model of the creation and the update requests.
 *
 * References between the parts of a question travel as the id of the referenced part, or its client-side temporary
 * id while it is unsaved.
 *
 * @param question the question to save
 * @param partId selects the id each part sends
 * @returns the request model of the question
 * @throws Error if the question carries no recognised type
 */
export function toQuizQuestionRequest(question: QuizQuestion, partId: PartIdSelector): QuizQuestionRequest {
    switch (question.type) {
        case QuizQuestionType.MULTIPLE_CHOICE:
            return toMultipleChoiceQuestionRequest(question, partId);
        case QuizQuestionType.DRAG_AND_DROP:
            return toDragAndDropQuestionRequest(question, partId);
        case QuizQuestionType.SHORT_ANSWER:
            return toShortAnswerQuestionRequest(question as ShortAnswerQuestion, partId);
        default:
            throw new Error(`Unsupported quiz question type: ${question.type}`);
    }
}
