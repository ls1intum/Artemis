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

export type QuizQuestionReEvaluateDTO = MultipleChoiceQuestionReEvaluateDTO | DragAndDropQuestionReEvaluateDTO | ShortAnswerQuestionReEvaluateDTO;

/**
 * DTO for re-evaluating a quiz exercise, containing essential properties stripped for evaluation.
 */
export interface QuizExerciseReEvaluateDTO {
    title?: string;
    includedInOverallScore?: IncludedInOverallScore;
    randomizeQuestionOrder?: boolean;
    quizQuestions?: QuizQuestionReEvaluateDTO[];
}

// Multiple Choice Question Group
/**
 * DTO for re-evaluating a multiple-choice question.
 */
export interface MultipleChoiceQuestionReEvaluateDTO {
    type: 'multiple-choice';
    id?: number;
    title?: string;
    scoringType?: ScoringType;
    randomizeOrder?: boolean;
    invalid?: boolean;
    text?: string;
    hint?: string;
    explanation?: string;
    answerOptions?: AnswerOptionReEvaluateDTO[];
}

/**
 * DTO for re-evaluating an answer option in a multiple-choice question.
 */
export interface AnswerOptionReEvaluateDTO {
    id?: number;
    text?: string;
    hint?: string;
    explanation?: string;
    isCorrect?: boolean;
    invalid?: boolean;
}

// Drag and Drop Question Group
/**
 * DTO for re-evaluating a drag-and-drop question.
 */
export interface DragAndDropQuestionReEvaluateDTO {
    type: 'drag-and-drop';
    id?: number;
    title?: string;
    text?: string;
    hint?: string;
    explanation?: string;
    scoringType?: ScoringType;
    randomizeOrder?: boolean;
    invalid?: boolean;
    dropLocations?: DropLocationReEvaluateDTO[];
    dragItems?: DragItemReEvaluateDTO[];
    correctMappings?: CorrectMappingReEvaluateDTO[];
}

/**
 * DTO for re-evaluating a drop location in a drag-and-drop question.
 */
export interface DropLocationReEvaluateDTO {
    id?: number;
    invalid?: boolean;
}

/**
 * DTO for re-evaluating a drag item in a drag-and-drop question.
 */
export interface DragItemReEvaluateDTO {
    id?: number;
    invalid?: boolean;
    text?: string;
    pictureFilePath?: string;
}

/**
 * DTO for re-evaluating a correct mapping in a drag-and-drop question.
 */
export interface CorrectMappingReEvaluateDTO {
    dragItemId?: number;
    dropLocationId?: number;
}

// Short Answer Question Group
/**
 * DTO for re-evaluating a short-answer question.
 */
export interface ShortAnswerQuestionReEvaluateDTO {
    type: 'short-answer';
    id?: number;
    title?: string;
    text?: string;
    scoringType?: ScoringType;
    randomizeOrder?: boolean;
    invalid?: boolean;
    similarityValue?: number;
    matchLetterCase?: boolean;
    spots?: ShortAnswerSpotReEvaluateDTO[];
    solutions?: ShortAnswerSolutionReEvaluateDTO[];
    correctMappings?: ShortAnswerMappingReEvaluateDTO[];
}

/**
 * DTO for re-evaluating a short-answer spot.
 */
export interface ShortAnswerSpotReEvaluateDTO {
    id?: number;
    invalid?: boolean;
}

/**
 * DTO for re-evaluating a short-answer solution.
 */
export interface ShortAnswerSolutionReEvaluateDTO {
    id?: number;
    text?: string;
    invalid?: boolean;
}

/**
 * DTO for re-evaluating a short-answer mapping.
 */
export interface ShortAnswerMappingReEvaluateDTO {
    solutionId?: number;
    spotId?: number;
}

// Conversion Functions

/**
 * Converts a QuizExercise model to its re-evaluation DTO.
 * @param quizExercise The source QuizExercise instance.
 * @returns The corresponding QuizExerciseReEvaluateDTO.
 */
export function convertQuizExerciseToReEvaluateDTO(quizExercise: QuizExercise): QuizExerciseReEvaluateDTO {
    return {
        title: quizExercise.title,
        includedInOverallScore: quizExercise.includedInOverallScore,
        randomizeQuestionOrder: quizExercise.randomizeQuestionOrder,
        quizQuestions: quizExercise.quizQuestions?.map((question) => {
            if (question instanceof MultipleChoiceQuestion) {
                return convertMultipleChoiceQuestionToReEvaluateDTO(question);
            } else if (question instanceof DragAndDropQuestion) {
                return convertDragAndDropQuestionToReEvaluateDTO(question);
            } else if (question instanceof ShortAnswerQuestion) {
                return convertShortAnswerQuestionToReEvaluateDTO(question);
            } else {
                throw new Error(`Unknown question type: ${question.constructor.name}`);
            }
        }),
    };
}

/**
 * Converts a MultipleChoiceQuestion to its re-evaluation DTO.
 * @param question The source MultipleChoiceQuestion instance.
 * @returns The corresponding MultipleChoiceQuestionReEvaluateDTO.
 */
function convertMultipleChoiceQuestionToReEvaluateDTO(question: MultipleChoiceQuestion): MultipleChoiceQuestionReEvaluateDTO {
    return {
        type: 'multiple-choice',
        id: question.id,
        title: question.title,
        scoringType: question.scoringType,
        randomizeOrder: question.randomizeOrder,
        invalid: question.invalid,
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        answerOptions: question.answerOptions?.map(convertAnswerOptionToReEvaluateDTO),
    };
}

/**
 * Converts an AnswerOption to its re-evaluation DTO.
 * @param option The source AnswerOption instance.
 * @returns The corresponding AnswerOptionReEvaluateDTO.
 */
function convertAnswerOptionToReEvaluateDTO(option: AnswerOption): AnswerOptionReEvaluateDTO {
    return {
        id: option.id,
        text: option.text,
        hint: option.hint,
        explanation: option.explanation,
        isCorrect: option.isCorrect,
        invalid: option.invalid,
    };
}

/**
 * Converts a DragAndDropQuestion to its re-evaluation DTO.
 * @param question The source DragAndDropQuestion instance.
 * @returns The corresponding DragAndDropQuestionReEvaluateDTO.
 */
function convertDragAndDropQuestionToReEvaluateDTO(question: DragAndDropQuestion): DragAndDropQuestionReEvaluateDTO {
    return {
        type: 'drag-and-drop',
        id: question.id,
        title: question.title,
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        scoringType: question.scoringType,
        randomizeOrder: question.randomizeOrder,
        invalid: question.invalid,
        dropLocations: question.dropLocations?.map(convertDropLocationToReEvaluateDTO),
        dragItems: question.dragItems?.map(convertDragItemToReEvaluateDTO),
        correctMappings: question.correctMappings?.map(convertCorrectMappingToReEvaluateDTO),
    };
}

/**
 * Converts a DropLocation to its re-evaluation DTO.
 * @param location The source DropLocation instance.
 * @returns The corresponding DropLocationReEvaluateDTO.
 */
function convertDropLocationToReEvaluateDTO(location: DropLocation): DropLocationReEvaluateDTO {
    return {
        id: location.id,
        invalid: location.invalid,
    };
}

/**
 * Converts a DragItem to its re-evaluation DTO.
 * @param item The source DragItem instance.
 * @returns The corresponding DragItemReEvaluateDTO.
 */
function convertDragItemToReEvaluateDTO(item: DragItem): DragItemReEvaluateDTO {
    return {
        id: item.id,
        invalid: item.invalid,
        text: item.text,
        pictureFilePath: item.pictureFilePath,
    };
}

/**
 * Converts a DragAndDropMapping to its re-evaluation DTO.
 * @param mapping The source DragAndDropMapping instance.
 * @returns The corresponding CorrectMappingReEvaluateDTO.
 */
function convertCorrectMappingToReEvaluateDTO(mapping: DragAndDropMapping): CorrectMappingReEvaluateDTO {
    return {
        dragItemId: mapping.dragItem?.id,
        dropLocationId: mapping.dropLocation?.id,
    };
}

/**
 * Converts a ShortAnswerQuestion to its re-evaluation DTO.
 * @param question The source ShortAnswerQuestion instance.
 * @returns The corresponding ShortAnswerQuestionReEvaluateDTO.
 */
function convertShortAnswerQuestionToReEvaluateDTO(question: ShortAnswerQuestion): ShortAnswerQuestionReEvaluateDTO {
    return {
        type: 'short-answer',
        id: question.id,
        title: question.title,
        text: question.text,
        scoringType: question.scoringType,
        randomizeOrder: question.randomizeOrder,
        invalid: question.invalid,
        similarityValue: question.similarityValue,
        matchLetterCase: question.matchLetterCase,
        spots: question.spots?.map(convertShortAnswerSpotToReEvaluateDTO),
        solutions: question.solutions?.map(convertShortAnswerSolutionToReEvaluateDTO),
        correctMappings: question.correctMappings?.map(convertShortAnswerMappingToReEvaluateDTO),
    };
}

/**
 * Converts a ShortAnswerSpot to its re-evaluation DTO.
 * @param spot The source ShortAnswerSpot instance.
 * @returns The corresponding ShortAnswerSpotReEvaluateDTO.
 */
function convertShortAnswerSpotToReEvaluateDTO(spot: ShortAnswerSpot): ShortAnswerSpotReEvaluateDTO {
    return {
        id: spot.id,
        invalid: spot.invalid,
    };
}

/**
 * Converts a ShortAnswerSolution to its re-evaluation DTO.
 * @param solution The source ShortAnswerSolution instance.
 * @returns The corresponding ShortAnswerSolutionReEvaluateDTO.
 */
function convertShortAnswerSolutionToReEvaluateDTO(solution: ShortAnswerSolution): ShortAnswerSolutionReEvaluateDTO {
    return {
        id: solution.id,
        text: solution.text,
        invalid: solution.invalid,
    };
}

/**
 * Converts a ShortAnswerMapping to its re-evaluation DTO.
 * @param mapping The source ShortAnswerMapping instance.
 * @returns The corresponding ShortAnswerMappingReEvaluateDTO.
 */
function convertShortAnswerMappingToReEvaluateDTO(mapping: ShortAnswerMapping): ShortAnswerMappingReEvaluateDTO {
    return {
        solutionId: mapping.solution?.id,
        spotId: mapping.spot?.id,
    };
}
