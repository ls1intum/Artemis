import { QuizQuestionType, ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerMapping } from 'app/quiz/shared/entities/short-answer-mapping.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';

export class QuizQuestionWithSolutionDTO {
    public id?: number;
    public title?: string;
    public text?: string;
    public hint?: string;
    public explanation?: string;
    public points?: number;
    public scoringType?: ScoringType;
    public randomizeOrder = true;
    public invalid = false;
    public type?: QuizQuestionType;
    public exportQuiz = false;
    public backgroundFilePath?: string;
    public dropLocations?: DropLocation[];
    public dragItems?: DragItem[];
    public correctMappings?: DragAndDropMapping[] | ShortAnswerMapping[];
    public spots?: ShortAnswerSpot[];
    public solutions?: ShortAnswerSolution[];
    public similarityValue?: number;
    public matchLetterCase?: boolean;
    public answerOptions?: AnswerOption[];
    public singleChoice?: boolean;
}

export class QuizQuestionTraining {
    public quizQuestionWithSolutionDTO?: QuizQuestionWithSolutionDTO;
    public isRated: boolean;
    public questionIds: number[];
    public isNewSession: boolean;
}
