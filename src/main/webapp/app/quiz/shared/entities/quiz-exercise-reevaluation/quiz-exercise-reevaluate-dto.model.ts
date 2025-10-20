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

export interface QuizExerciseReEvaluateDTO {
    title?: string;
    includedInOverallScore?: IncludedInOverallScore;
    randomizeQuestionOrder?: boolean;
    quizQuestions?: QuizQuestionReEvaluateDTO[];
}

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
                throw new Error('Unknown question type');
            }
        }),
    };
}

export interface MultipleChoiceQuestionReEvaluateDTO {
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

function convertMultipleChoiceQuestionToReEvaluateDTO(multipleChoiceQuestion: MultipleChoiceQuestion): MultipleChoiceQuestionReEvaluateDTO {
    return {
        id: multipleChoiceQuestion.id,
        title: multipleChoiceQuestion.title,
        scoringType: multipleChoiceQuestion.scoringType,
        randomizeOrder: multipleChoiceQuestion.randomizeOrder,
        invalid: multipleChoiceQuestion.invalid,
        text: multipleChoiceQuestion.text,
        hint: multipleChoiceQuestion.hint,
        explanation: multipleChoiceQuestion.explanation,
        answerOptions: multipleChoiceQuestion.answerOptions?.map(convertAnswerOptionToReEvaluateDTO),
    };
}

export interface AnswerOptionReEvaluateDTO {
    id?: number;
    text?: string;
    hint?: string;
    explanation?: string;
    isCorrect?: boolean;
    invalid?: boolean;
}

function convertAnswerOptionToReEvaluateDTO(answerOption: AnswerOption): AnswerOptionReEvaluateDTO {
    return {
        id: answerOption.id,
        text: answerOption.text,
        hint: answerOption.hint,
        explanation: answerOption.explanation,
        isCorrect: answerOption.isCorrect,
        invalid: answerOption.invalid,
    };
}

export interface DragAndDropQuestionReEvaluateDTO {
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

function convertDragAndDropQuestionToReEvaluateDTO(dragAndDropQuestion: DragAndDropQuestion): DragAndDropQuestionReEvaluateDTO {
    return {
        id: dragAndDropQuestion.id,
        title: dragAndDropQuestion.title,
        text: dragAndDropQuestion.text,
        hint: dragAndDropQuestion.hint,
        explanation: dragAndDropQuestion.explanation,
        scoringType: dragAndDropQuestion.scoringType,
        randomizeOrder: dragAndDropQuestion.randomizeOrder,
        invalid: dragAndDropQuestion.invalid,
        dropLocations: dragAndDropQuestion.dropLocations?.map(convertDropLocationToReEvaluateDTO),
        dragItems: dragAndDropQuestion.dragItems?.map(convertDragItemToReEvaluateDTO),
        correctMappings: dragAndDropQuestion.correctMappings?.map(convertCorrectMappingToReEvaluateDTO),
    };
}

export interface DropLocationReEvaluateDTO {
    id?: number;
    invalid?: boolean;
}

function convertDropLocationToReEvaluateDTO(dropLocation: DropLocation): DropLocationReEvaluateDTO {
    return {
        id: dropLocation.id,
        invalid: dropLocation.invalid,
    };
}

export interface DragItemReEvaluateDTO {
    id?: number;
    invalid?: boolean;
    text?: string;
    pictureFilePath?: string;
}

function convertDragItemToReEvaluateDTO(dragItem: DragItem): DragItemReEvaluateDTO {
    return {
        id: dragItem.id,
        invalid: dragItem.invalid,
        text: dragItem.text,
        pictureFilePath: dragItem.pictureFilePath,
    };
}

export interface CorrectMappingReEvaluateDTO {
    dragItemId?: number;
    dropLocationId?: number;
}

function convertCorrectMappingToReEvaluateDTO(mapping: DragAndDropMapping) {
    return {
        dragItemId: mapping.dragItem?.id,
        dropLocationId: mapping.dropLocation?.id,
    };
}

export interface ShortAnswerQuestionReEvaluateDTO {
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

function convertShortAnswerQuestionToReEvaluateDTO(shortAnswerQuestion: ShortAnswerQuestion): ShortAnswerQuestionReEvaluateDTO {
    return {
        id: shortAnswerQuestion.id,
        title: shortAnswerQuestion.title,
        text: shortAnswerQuestion.text,
        scoringType: shortAnswerQuestion.scoringType,
        randomizeOrder: shortAnswerQuestion.randomizeOrder,
        invalid: shortAnswerQuestion.invalid,
        similarityValue: shortAnswerQuestion.similarityValue,
        matchLetterCase: shortAnswerQuestion.matchLetterCase,
        spots: shortAnswerQuestion.spots?.map(convertShortAnswerSpotToReEvaluateDTO),
        solutions: shortAnswerQuestion.solutions?.map(convertShortAnswerSolutionToReEvaluateDTO),
        correctMappings: shortAnswerQuestion.correctMappings?.map(convertShortAnswerMappingToReEvaluateDTO),
    };
}

export interface ShortAnswerSpotReEvaluateDTO {
    id?: number;
    invalid?: boolean;
}

function convertShortAnswerSpotToReEvaluateDTO(spot: ShortAnswerSpot): ShortAnswerSpotReEvaluateDTO {
    return {
        id: spot.id,
        invalid: spot.invalid,
    };
}

export interface ShortAnswerSolutionReEvaluateDTO {
    id?: number;
    text?: string;
    invalid?: boolean;
}

function convertShortAnswerSolutionToReEvaluateDTO(solution: ShortAnswerSolution): ShortAnswerSolutionReEvaluateDTO {
    return {
        id: solution.id,
        text: solution.text,
        invalid: solution.invalid,
    };
}

export interface ShortAnswerMappingReEvaluateDTO {
    solutionId?: number;
    spotId?: number;
}

function convertShortAnswerMappingToReEvaluateDTO(shortAnswerMapping: ShortAnswerMapping): ShortAnswerMappingReEvaluateDTO {
    return {
        solutionId: shortAnswerMapping.solution?.id,
        spotId: shortAnswerMapping.spot?.id,
    };
}
