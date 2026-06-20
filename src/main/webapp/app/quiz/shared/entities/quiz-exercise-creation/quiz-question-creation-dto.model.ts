import { QuizQuestion, QuizQuestionType, ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';

export type QuizQuestionCreateDTO = MultipleChoiceQuestionCreateDTO | DragAndDropQuestionCreateDTO | ShortAnswerQuestionCreateDTO;

export interface MultipleChoiceQuestionCreateDTO {
    type: 'multiple-choice';
    title: string;
    text?: string;
    hint?: string;
    explanation?: string;
    points: number;
    scoringType: ScoringType;
    randomizeOrder: boolean;
    answerOptions: AnswerOptionCreateDTO[];
    singleChoice: boolean;
}

export interface AnswerOptionCreateDTO {
    text: string;
    hint?: string;
    explanation?: string;
    isCorrect: boolean;
}

export interface DragAndDropQuestionCreateDTO {
    type: 'drag-and-drop';
    title: string;
    text?: string;
    hint?: string;
    explanation?: string;
    points: number;
    scoringType: ScoringType;
    randomizeOrder: boolean;
    backgroundFilePath?: string;
    dropLocations: DropLocationCreateDTO[];
    dragItems: DragItemCreateDTO[];
    correctMappings: DragAndDropMappingCreateDTO[];
}

export interface DropLocationCreateDTO {
    tempID: number;
    posX: number;
    posY: number;
    width: number;
    height: number;
}

export interface DragItemCreateDTO {
    tempID: number;
    text?: string;
    pictureFilePath?: string;
}

export interface DragAndDropMappingCreateDTO {
    dragItemTempId: number;
    dropLocationTempId: number;
}

export interface ShortAnswerQuestionCreateDTO {
    type: 'short-answer';
    title: string;
    text?: string;
    hint?: string;
    explanation?: string;
    points: number;
    scoringType?: ScoringType;
    randomizeOrder: boolean;
    spots: ShortAnswerSpotCreateDTO[];
    solutions: ShortAnswerSolutionCreateDTO[];
    correctMappings: ShortAnswerMappingCreateDTO[];
    similarityValue: number;
    matchLetterCase: boolean;
}

export interface ShortAnswerSpotCreateDTO {
    tempID: number;
    spotNr: number;
    width: number;
}

export interface ShortAnswerSolutionCreateDTO {
    tempID: number;
    text: string;
}

export interface ShortAnswerMappingCreateDTO {
    solutionTempId: number;
    spotTempId: number;
}

function convertAnswerOptionToDTO(option: AnswerOption): AnswerOptionCreateDTO {
    return {
        text: option.text ?? '',
        hint: option.hint,
        explanation: option.explanation,
        isCorrect: option.isCorrect ?? false,
    };
}

function convertMultipleChoiceQuestionToDTO(question: MultipleChoiceQuestion): MultipleChoiceQuestionCreateDTO {
    return {
        type: 'multiple-choice',
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        scoringType: question.scoringType ?? ScoringType.ALL_OR_NOTHING,
        randomizeOrder: question.randomizeOrder,
        answerOptions: (question.answerOptions ?? []).map(convertAnswerOptionToDTO),
        singleChoice: question.singleChoice ?? false,
    };
}

function convertDragAndDropQuestionToDTO(question: DragAndDropQuestion): DragAndDropQuestionCreateDTO {
    return {
        type: 'drag-and-drop',
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        scoringType: question.scoringType ?? ScoringType.ALL_OR_NOTHING,
        randomizeOrder: question.randomizeOrder,
        backgroundFilePath: question.backgroundFilePath,
        dropLocations: (question.dropLocations ?? []).map((dl) => ({
            tempID: dl.tempID ?? 0,
            posX: dl.posX ?? 0,
            posY: dl.posY ?? 0,
            width: dl.width ?? 0,
            height: dl.height ?? 0,
        })),
        dragItems: (question.dragItems ?? []).map((di) => ({
            tempID: di.tempID ?? 0,
            text: di.text,
            pictureFilePath: di.pictureFilePath,
        })),
        correctMappings: (question.correctMappings ?? []).map((mapping) => ({
            dragItemTempId: mapping.dragItem?.tempID ?? 0,
            dropLocationTempId: mapping.dropLocation?.tempID ?? 0,
        })),
    };
}

function convertShortAnswerQuestionToDTO(question: ShortAnswerQuestion): ShortAnswerQuestionCreateDTO {
    return {
        type: 'short-answer',
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        scoringType: question.scoringType, // Optional on server
        randomizeOrder: question.randomizeOrder,
        spots: (question.spots ?? []).map((spot) => ({
            tempID: spot.tempID ?? 0,
            spotNr: spot.spotNr ?? 0,
            width: spot.width ?? 0,
        })),
        solutions: (question.solutions ?? []).map((solution) => ({
            tempID: solution.tempID ?? 0,
            text: solution.text ?? '',
        })),
        correctMappings: (question.correctMappings ?? []).map((mapping) => ({
            solutionTempId: mapping.solution?.tempID ?? 0,
            spotTempId: mapping.spot?.tempID ?? 0,
        })),
        similarityValue: question.similarityValue ?? 85,
        matchLetterCase: question.matchLetterCase ?? false,
    };
}

export function convertQuizQuestionToDTO(question: QuizQuestion): QuizQuestionCreateDTO {
    switch (question.type) {
        case QuizQuestionType.MULTIPLE_CHOICE:
            return convertMultipleChoiceQuestionToDTO(question as MultipleChoiceQuestion);
        case QuizQuestionType.DRAG_AND_DROP:
            return convertDragAndDropQuestionToDTO(question as DragAndDropQuestion);
        case QuizQuestionType.SHORT_ANSWER:
            return convertShortAnswerQuestionToDTO(question as ShortAnswerQuestion);
        default:
            throw new Error(`Unsupported quiz question type: ${question.type}`);
    }
}

export function convertQuizQuestionsToDTOs(questions: QuizQuestion[]): QuizQuestionCreateDTO[] {
    return questions.map(convertQuizQuestionToDTO);
}
