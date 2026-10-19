import { QuizQuestionType, ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerMapping } from 'app/quiz/shared/entities/short-answer-mapping.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';

export interface QuizQuestionWithSolutionDTO {
    id?: number;
    title?: string;
    text?: string;
    hint?: string;
    explanation?: string;
    points?: number;
    scoringType?: ScoringType;
    // Required (not optional) to stay assignable to the QuizQuestion class, which declares these as non-optional.
    randomizeOrder: boolean;
    invalid: boolean;
    type?: QuizQuestionType;
    exportQuiz: boolean;
    backgroundFilePath?: string;
    dropLocations?: DropLocation[];
    dragItems?: DragItem[];
    correctMappings?: DragAndDropMapping[] | ShortAnswerMapping[];
    spots?: ShortAnswerSpot[];
    solutions?: ShortAnswerSolution[];
    similarityValue?: number;
    matchLetterCase?: boolean;
    answerOptions?: AnswerOption[];
    singleChoice?: boolean;
}

export interface QuizQuestionTraining {
    quizQuestionWithSolutionDTO?: QuizQuestionWithSolutionDTO;
    isRated: boolean;
    questionIds: number[];
    isNewSession: boolean;
}
