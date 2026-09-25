import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';
import { ShortAnswerMapping } from 'app/quiz/shared/entities/short-answer-mapping.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { AnswerOptionReEvaluate } from 'app/openapi/model/answer-option-re-evaluate';
import { DragAndDropMappingReEvaluate } from 'app/openapi/model/drag-and-drop-mapping-re-evaluate';
import { DragAndDropQuestionReEvaluate } from 'app/openapi/model/drag-and-drop-question-re-evaluate';
import { DragItemReEvaluate } from 'app/openapi/model/drag-item-re-evaluate';
import { DropLocationReEvaluate } from 'app/openapi/model/drop-location-re-evaluate';
import { MultipleChoiceQuestionReEvaluate } from 'app/openapi/model/multiple-choice-question-re-evaluate';
import { QuizExerciseReEvaluate } from 'app/openapi/model/quiz-exercise-re-evaluate';
import { ShortAnswerMappingReEvaluate } from 'app/openapi/model/short-answer-mapping-re-evaluate';
import { ShortAnswerQuestionReEvaluate } from 'app/openapi/model/short-answer-question-re-evaluate';
import { ShortAnswerSolutionReEvaluate } from 'app/openapi/model/short-answer-solution-re-evaluate';
import { ShortAnswerSpotReEvaluate } from 'app/openapi/model/short-answer-spot-re-evaluate';

/**
 * Converts a quiz exercise into the request model of the re-evaluation endpoint.
 *
 * Re-evaluation only ever runs on a saved quiz, so every existing part carries its id. A solution added during
 * re-evaluation has none yet and is referenced by its client-side temporary id instead. Missing scalar values fall back
 * to the defaults the editor assigns, as in the update request.
 *
 * @param quizExercise the saved exercise, with the instructor's corrections applied
 * @returns the request model of the exercise
 */
export function toQuizExerciseReEvaluate(quizExercise: QuizExercise): QuizExerciseReEvaluate {
    return {
        title: quizExercise.title ?? '',
        includedInOverallScore: quizExercise.includedInOverallScore ?? IncludedInOverallScore.INCLUDED_COMPLETELY,
        randomizeQuestionOrder: quizExercise.randomizeQuestionOrder ?? true,
        quizQuestions: (quizExercise.quizQuestions ?? []).map((question) => {
            if (question.type === 'multiple-choice') {
                return convertMultipleChoiceQuestionToReEvaluateDTO(question);
            } else if (question.type === 'drag-and-drop') {
                return convertDragAndDropQuestionToReEvaluateDTO(question);
            } else if (question.type === 'short-answer') {
                return convertShortAnswerQuestionToReEvaluateDTO(question as ShortAnswerQuestion);
            } else {
                throw new Error(`Unknown question type: ${question.constructor.name}`);
            }
        }),
    };
}

/**
 * Converts a MultipleChoiceQuestion to its re-evaluation DTO.
 * @param question The source MultipleChoiceQuestion instance.
 * @returns The corresponding MultipleChoiceQuestionReEvaluate.
 */
function convertMultipleChoiceQuestionToReEvaluateDTO(question: MultipleChoiceQuestion): MultipleChoiceQuestionReEvaluate {
    return {
        type: 'multiple-choice',
        id: question.id!,
        title: question.title ?? '',
        scoringType: question.scoringType ?? ScoringType.ALL_OR_NOTHING,
        randomizeOrder: question.randomizeOrder ?? false,
        invalid: question.invalid ?? false,
        text: question.text ?? '',
        hint: question.hint,
        explanation: question.explanation,
        answerOptions: (question.answerOptions ?? []).map(convertAnswerOptionToReEvaluateDTO),
    };
}

/**
 * Converts an AnswerOption to its re-evaluation DTO.
 * @param option The source AnswerOption instance.
 * @returns The corresponding AnswerOptionReEvaluate.
 */
function convertAnswerOptionToReEvaluateDTO(option: AnswerOption): AnswerOptionReEvaluate {
    return {
        id: option.id!,
        text: option.text ?? '',
        hint: option.hint,
        explanation: option.explanation,
        isCorrect: option.isCorrect ?? false,
        invalid: option.invalid ?? false,
    };
}

/**
 * Converts a DragAndDropQuestion to its re-evaluation DTO.
 * @param question The source DragAndDropQuestion instance.
 * @returns The corresponding DragAndDropQuestionReEvaluate.
 */
function convertDragAndDropQuestionToReEvaluateDTO(question: DragAndDropQuestion): DragAndDropQuestionReEvaluate {
    return {
        type: 'drag-and-drop',
        id: question.id!,
        title: question.title ?? '',
        text: question.text ?? '',
        hint: question.hint,
        explanation: question.explanation,
        scoringType: question.scoringType ?? ScoringType.PROPORTIONAL_WITH_PENALTY,
        randomizeOrder: question.randomizeOrder ?? false,
        invalid: question.invalid ?? false,
        dropLocations: (question.dropLocations ?? []).map(convertDropLocationToReEvaluateDTO),
        dragItems: (question.dragItems ?? []).map(convertDragItemToReEvaluateDTO),
        correctMappings: (question.correctMappings ?? []).map(convertCorrectMappingToReEvaluateDTO),
    };
}

/**
 * Converts a DropLocation to its re-evaluation DTO.
 * @param location The source DropLocation instance.
 * @returns The corresponding DropLocationReEvaluate.
 */
function convertDropLocationToReEvaluateDTO(location: DropLocation): DropLocationReEvaluate {
    return {
        id: location.id!,
        invalid: location.invalid ?? false,
    };
}

/**
 * Converts a DragItem to its re-evaluation DTO.
 * @param item The source DragItem instance.
 * @returns The corresponding DragItemReEvaluate.
 */
function convertDragItemToReEvaluateDTO(item: DragItem): DragItemReEvaluate {
    return {
        id: item.id!,
        invalid: item.invalid ?? false,
        text: item.text,
        pictureFilePath: item.pictureFilePath,
    };
}

/**
 * Converts a DragAndDropMapping to its re-evaluation DTO.
 * @param mapping The source DragAndDropMapping instance.
 * @returns The corresponding DragAndDropMappingReEvaluate.
 */
function convertCorrectMappingToReEvaluateDTO(mapping: DragAndDropMapping): DragAndDropMappingReEvaluate {
    return {
        dragItemId: mapping.dragItem!.id!,
        dropLocationId: mapping.dropLocation!.id!,
    };
}

/**
 * Converts a ShortAnswerQuestion to its re-evaluation DTO.
 * @param question The source ShortAnswerQuestion instance.
 * @returns The corresponding ShortAnswerQuestionReEvaluate.
 */
function convertShortAnswerQuestionToReEvaluateDTO(question: ShortAnswerQuestion): ShortAnswerQuestionReEvaluate {
    return {
        type: 'short-answer',
        id: question.id!,
        title: question.title ?? '',
        text: question.text ?? '',
        scoringType: question.scoringType ?? ScoringType.PROPORTIONAL_WITHOUT_PENALTY,
        randomizeOrder: question.randomizeOrder ?? false,
        invalid: question.invalid ?? false,
        similarityValue: question.similarityValue,
        matchLetterCase: question.matchLetterCase,
        spots: (question.spots ?? []).map(convertShortAnswerSpotToReEvaluateDTO),
        solutions: (question.solutions ?? []).map(convertShortAnswerSolutionToReEvaluateDTO),
        correctMappings: (question.correctMappings ?? []).map(convertShortAnswerMappingToReEvaluateDTO),
    };
}

/**
 * Converts a ShortAnswerSpot to its re-evaluation DTO.
 * @param spot The source ShortAnswerSpot instance.
 * @returns The corresponding ShortAnswerSpotReEvaluate.
 */
function convertShortAnswerSpotToReEvaluateDTO(spot: ShortAnswerSpot): ShortAnswerSpotReEvaluate {
    return {
        id: spot.id!,
        invalid: spot.invalid ?? false,
    };
}

/**
 * Converts a ShortAnswerSolution to its re-evaluation DTO.
 * @param solution The source ShortAnswerSolution instance.
 * @returns The corresponding ShortAnswerSolutionReEvaluate.
 */
function convertShortAnswerSolutionToReEvaluateDTO(solution: ShortAnswerSolution): ShortAnswerSolutionReEvaluate {
    if (!solution.id) {
        return {
            tempID: solution.tempID,
            text: solution.text ?? '',
            invalid: solution.invalid ?? false,
        };
    } else {
        return {
            id: solution.id,
            text: solution.text ?? '',
            invalid: solution.invalid ?? false,
        };
    }
}

/**
 * Converts a ShortAnswerMapping to its re-evaluation DTO.
 * @param mapping The source ShortAnswerMapping instance.
 * @returns The corresponding ShortAnswerMappingReEvaluate.
 */
function convertShortAnswerMappingToReEvaluateDTO(mapping: ShortAnswerMapping): ShortAnswerMappingReEvaluate {
    if (!mapping.solution?.id) {
        return {
            solutionTempID: mapping.solution!.tempID!,
            spotId: mapping.spot!.id!,
        };
    } else {
        return {
            solutionId: mapping.solution.id,
            spotId: mapping.spot!.id!,
        };
    }
}
