import type { ShortAnswerMapping } from './short-answer-mapping';
import type { AnswerOptionWithSolution } from './answer-option-with-solution';
import type { ShortAnswerSpot } from './short-answer-spot';
import type { DragItem } from './drag-item';
import type { DropLocation } from './drop-location';
import type { ShortAnswerSolution } from './short-answer-solution';

export interface QuizQuestionWithSolution {
    type: string;
    id?: number;
    title?: string;
    text?: string;
    hint?: string;
    points?: number;
    scoringType?: QuizQuestionWithSolutionScoringTypeEnum;
    randomizeOrder?: boolean;
    invalid?: boolean;
    explanation?: string;
    answerOptions?: Array<AnswerOptionWithSolution>;
    singleChoice?: boolean;
    backgroundFilePath?: string;
    dropLocations?: Array<DropLocation>;
    dragItems?: Array<DragItem>;
    correctMappings?: Array<ShortAnswerMapping>;
    spots?: Array<ShortAnswerSpot>;
    solutions?: Array<ShortAnswerSolution>;
    similarityValue?: number;
    matchLetterCase?: boolean;
}

export type QuizQuestionWithSolutionScoringTypeEnum = 'ALL_OR_NOTHING' | 'PROPORTIONAL_WITH_PENALTY' | 'PROPORTIONAL_WITHOUT_PENALTY';

export const QuizQuestionWithSolutionScoringTypeEnum = {
    AllOrNothing: 'ALL_OR_NOTHING' as const,
    ProportionalWithPenalty: 'PROPORTIONAL_WITH_PENALTY' as const,
    ProportionalWithoutPenalty: 'PROPORTIONAL_WITHOUT_PENALTY' as const,
} as const;

export const QuizQuestionWithSolutionScoringTypeEnumValues = ['ALL_OR_NOTHING', 'PROPORTIONAL_WITH_PENALTY', 'PROPORTIONAL_WITHOUT_PENALTY'] as const;

