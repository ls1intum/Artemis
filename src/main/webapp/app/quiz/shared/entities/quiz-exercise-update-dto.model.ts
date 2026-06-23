import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { DifficultyLevel, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizBatch, QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import dayjs from 'dayjs/esm';
import { CompetencyLinkDTO } from 'app/exercise/shared/exercise-update-shared-dto.model';
import { QuizQuestion, QuizQuestionType, ScoringType } from './quiz-question.model';
import { MultipleChoiceQuestion } from './multiple-choice-question.model';
import { AnswerOption } from './answer-option.model';
import { DragAndDropQuestion } from './drag-and-drop-question.model';
import { DragItem } from './drag-item.model';
import { DropLocation } from './drop-location.model';
import { DragAndDropMapping } from './drag-and-drop-mapping.model';
import { ShortAnswerQuestion } from './short-answer-question.model';
import { ShortAnswerSpot } from './short-answer-spot.model';
import { ShortAnswerSolution } from './short-answer-solution.model';
import { ShortAnswerMapping } from './short-answer-mapping.model';

export type QuizQuestionUpdateDTO = MultipleChoiceQuestionUpdateDTO | DragAndDropQuestionUpdateDTO | ShortAnswerQuestionUpdateDTO;

export interface QuizExerciseUpdateDTO {
    title?: string;
    channelName?: string;
    categories?: ExerciseCategory[];
    competencyLinks?: CompetencyLinkDTO[];
    difficulty?: DifficultyLevel;
    duration?: number;
    randomizeQuestionOrder?: boolean;
    quizMode?: QuizMode;
    quizBatches?: QuizBatch[];
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    includedInOverallScore?: IncludedInOverallScore;
    quizQuestions?: QuizQuestionUpdateDTO[];
}

export interface MultipleChoiceQuestionUpdateDTO {
    type: 'multiple-choice';
    id?: number;
    title: string;
    text?: string;
    hint?: string;
    explanation?: string;
    points: number;
    scoringType: ScoringType;
    randomizeOrder: boolean;
    answerOptions: AnswerOptionUpdateDTO[];
    singleChoice: boolean;
}

export interface AnswerOptionUpdateDTO {
    id?: number;
    text: string;
    hint?: string;
    explanation?: string;
    isCorrect: boolean;
}

export interface DragAndDropQuestionUpdateDTO {
    type: 'drag-and-drop';
    id?: number;
    title: string;
    text?: string;
    hint?: string;
    explanation?: string;
    points: number;
    scoringType: ScoringType;
    randomizeOrder: boolean;
    backgroundFilePath?: string;
    dropLocations: DropLocationUpdateDTO[];
    dragItems: DragItemUpdateDTO[];
    correctMappings: DragAndDropMappingUpdateDTO[];
}

export interface DropLocationUpdateDTO {
    id?: number;
    tempID?: number;
    posX: number;
    posY: number;
    width: number;
    height: number;
}

export interface DragItemUpdateDTO {
    id?: number;
    tempID?: number;
    text?: string;
    pictureFilePath?: string;
}

export interface DragAndDropMappingUpdateDTO {
    id?: number;
    dragItemTempId: number;
    dropLocationTempId: number;
}

export interface ShortAnswerQuestionUpdateDTO {
    type: 'short-answer';
    id?: number;
    title: string;
    text?: string;
    hint?: string;
    explanation?: string;
    points: number;
    scoringType: ScoringType;
    randomizeOrder: boolean;
    spots: ShortAnswerSpotUpdateDTO[];
    solutions: ShortAnswerSolutionUpdateDTO[];
    correctMappings: ShortAnswerMappingUpdateDTO[];
    similarityValue: number;
    matchLetterCase: boolean;
}

export interface ShortAnswerSpotUpdateDTO {
    id?: number;
    tempID?: number;
    width: number;
    spotNr: number;
}

export interface ShortAnswerSolutionUpdateDTO {
    id?: number;
    tempID?: number;
    text: string;
}

export interface ShortAnswerMappingUpdateDTO {
    id?: number;
    solutionTempId: number;
    spotTempId: number;
}

function getEditorReferenceId(entity: { id?: number; tempID?: number } | undefined): number {
    return entity?.id ?? entity?.tempID ?? 0;
}

function getEditorTempId(entity: { id?: number; tempID?: number } | undefined): number | undefined {
    return entity?.id ?? entity?.tempID;
}

function convertAnswerOptionToUpdateDTO(option: AnswerOption): AnswerOptionUpdateDTO {
    return {
        id: option.id,
        text: option.text ?? '',
        hint: option.hint,
        explanation: option.explanation,
        isCorrect: option.isCorrect ?? false,
    };
}

function convertMultipleChoiceQuestionToUpdateDTO(question: MultipleChoiceQuestion): MultipleChoiceQuestionUpdateDTO {
    return {
        type: 'multiple-choice',
        id: question.id,
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        scoringType: question.scoringType ?? ScoringType.ALL_OR_NOTHING,
        randomizeOrder: question.randomizeOrder,
        answerOptions: (question.answerOptions ?? []).map(convertAnswerOptionToUpdateDTO),
        singleChoice: question.singleChoice ?? false,
    };
}

function convertDropLocationToUpdateDTO(dropLocation: DropLocation): DropLocationUpdateDTO {
    return {
        id: dropLocation.id,
        tempID: getEditorTempId(dropLocation),
        posX: dropLocation.posX ?? 0,
        posY: dropLocation.posY ?? 0,
        width: dropLocation.width ?? 0,
        height: dropLocation.height ?? 0,
    };
}

function convertDragItemToUpdateDTO(dragItem: DragItem): DragItemUpdateDTO {
    return {
        id: dragItem.id,
        tempID: getEditorTempId(dragItem),
        text: dragItem.text,
        pictureFilePath: dragItem.pictureFilePath,
    };
}

function convertDragAndDropMappingToUpdateDTO(mapping: DragAndDropMapping): DragAndDropMappingUpdateDTO {
    return {
        id: mapping.id,
        dragItemTempId: getEditorReferenceId(mapping.dragItem),
        dropLocationTempId: getEditorReferenceId(mapping.dropLocation),
    };
}

function convertDragAndDropQuestionToUpdateDTO(question: DragAndDropQuestion): DragAndDropQuestionUpdateDTO {
    return {
        type: 'drag-and-drop',
        id: question.id,
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        scoringType: question.scoringType ?? ScoringType.PROPORTIONAL_WITH_PENALTY,
        randomizeOrder: question.randomizeOrder,
        backgroundFilePath: question.backgroundFilePath,
        dropLocations: (question.dropLocations ?? []).map(convertDropLocationToUpdateDTO),
        dragItems: (question.dragItems ?? []).map(convertDragItemToUpdateDTO),
        correctMappings: (question.correctMappings ?? []).map(convertDragAndDropMappingToUpdateDTO),
    };
}

function convertShortAnswerSpotToUpdateDTO(spot: ShortAnswerSpot): ShortAnswerSpotUpdateDTO {
    return {
        id: spot.id,
        tempID: getEditorTempId(spot),
        width: spot.width ?? 0,
        spotNr: spot.spotNr ?? 0,
    };
}

function convertShortAnswerSolutionToUpdateDTO(solution: ShortAnswerSolution): ShortAnswerSolutionUpdateDTO {
    return {
        id: solution.id,
        tempID: getEditorTempId(solution),
        text: solution.text ?? '',
    };
}

function convertShortAnswerMappingToUpdateDTO(mapping: ShortAnswerMapping): ShortAnswerMappingUpdateDTO {
    return {
        id: mapping.id,
        solutionTempId: getEditorReferenceId(mapping.solution),
        spotTempId: getEditorReferenceId(mapping.spot),
    };
}

function convertShortAnswerQuestionToUpdateDTO(question: ShortAnswerQuestion): ShortAnswerQuestionUpdateDTO {
    return {
        type: 'short-answer',
        id: question.id,
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        scoringType: question.scoringType ?? ScoringType.PROPORTIONAL_WITHOUT_PENALTY,
        randomizeOrder: question.randomizeOrder,
        spots: (question.spots ?? []).map(convertShortAnswerSpotToUpdateDTO),
        solutions: (question.solutions ?? []).map(convertShortAnswerSolutionToUpdateDTO),
        correctMappings: (question.correctMappings ?? []).map(convertShortAnswerMappingToUpdateDTO),
        similarityValue: question.similarityValue ?? 85,
        matchLetterCase: question.matchLetterCase ?? false,
    };
}

function convertQuizQuestionToUpdateDTO(question: QuizQuestion): QuizQuestionUpdateDTO {
    switch (question.type) {
        case QuizQuestionType.MULTIPLE_CHOICE:
            return convertMultipleChoiceQuestionToUpdateDTO(question as MultipleChoiceQuestion);
        case QuizQuestionType.DRAG_AND_DROP:
            return convertDragAndDropQuestionToUpdateDTO(question as DragAndDropQuestion);
        case QuizQuestionType.SHORT_ANSWER:
            return convertShortAnswerQuestionToUpdateDTO(question as ShortAnswerQuestion);
        default:
            throw new Error(`Unsupported quiz question type: ${question.type}`);
    }
}

export function toQuizExerciseUpdateDTO(quizExercise: QuizExercise): QuizExerciseUpdateDTO {
    return {
        title: quizExercise.title,
        channelName: quizExercise.channelName,
        categories: quizExercise.categories,
        competencyLinks: (quizExercise.competencyLinks ?? []).map((link) => ({
            competency: { id: link.competency!.id! },
            weight: link.weight ?? 1,
        })),
        difficulty: quizExercise.difficulty,
        duration: quizExercise.duration,
        randomizeQuestionOrder: quizExercise.randomizeQuestionOrder,
        quizMode: quizExercise.quizMode,
        quizBatches: quizExercise.quizBatches,
        releaseDate: quizExercise.releaseDate,
        startDate: quizExercise.startDate,
        dueDate: quizExercise.dueDate,
        includedInOverallScore: quizExercise.includedInOverallScore,
        quizQuestions: quizExercise.quizQuestions ? quizExercise.quizQuestions.map(convertQuizQuestionToUpdateDTO) : undefined,
    };
}
