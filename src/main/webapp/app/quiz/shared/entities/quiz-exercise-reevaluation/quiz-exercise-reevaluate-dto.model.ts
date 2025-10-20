import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';

export type QuizQuestionReEvaluateDTO = MultipleChoiceQuestionReEvaluateDTO | DragAndDropQuestionReEvaluateDTO | ShortAnswerQuestionReEvaluateDTO;

export interface QuizExerciseReEvaluateDTO {
    title: string;
    includedInOverallScore: IncludedInOverallScore;
    randomizeQuestionOrder: boolean;
    quizQuestions: QuizQuestionReEvaluateDTO[];
}

export interface MultipleChoiceQuestionReEvaluateDTO {
    id: number;
    title: string;
    scoringType: ScoringType;
    randomizeOrder: boolean;
    invalid: boolean;
    text?: string;
    hint?: string;
    explanation?: string;
    answerOptions: AnswerOptionsReEvaluateDTO[];
}

export interface AnswerOptionsReEvaluateDTO {
    id: number;
    text: string;
    hint?: string;
    explanation?: string;
    isCorrect: boolean;
    invalid: boolean;
}

export interface DragAndDropQuestionReEvaluateDTO {
    id: number;
    title: string;
    text?: string;
    hint?: string;
    explanation?: string;
    scoringType: ScoringType;
    randomizeOrder: boolean;
    invalid: boolean;
    dropLocations: DropLocationReEvaluateDTO[];
    dragItems: DragItemReEvaluateDTO[];
    correctMappings: CorrectMappingReEvaluateDTO[];
}

export interface DropLocationReEvaluateDTO {
    id: number;
    invalid: boolean;
}

export interface DragItemReEvaluateDTO {
    id: number;
    invalid: boolean;
    text?: string;
    pictureFilePath?: string;
}

export interface CorrectMappingReEvaluateDTO {
    dragItemId: number;
    dropLocationId: number;
}

export interface ShortAnswerQuestionReEvaluateDTO {
    id: number;
    title: string;
    text: string;
    scoringType: ScoringType;
    randomizeOrder: boolean;
    invalid: boolean;
    similarityValue: number;
    matchLetterCase: boolean;
    spots: ShortAnswerSpotReEvaluateDTO[];
    solutions: ShortAnswerSolutionReEvaluateDTO[];
    correctMappings: ShortAnswerMappingReEvaluateDTO[];
}

export interface ShortAnswerSpotReEvaluateDTO {
    id: number;
    invalid: boolean;
}

export interface ShortAnswerSolutionReEvaluateDTO {
    id: number;
    text: string;
    invalid: boolean;
}

export interface ShortAnswerMappingReEvaluateDTO {
    solutionId: number;
    spotId: number;
}
