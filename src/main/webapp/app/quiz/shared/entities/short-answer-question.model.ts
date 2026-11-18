import { ShortAnswerMapping } from 'app/quiz/shared/entities/short-answer-mapping.model';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';

export class ShortAnswerQuestion extends QuizQuestion {
    public spots?: ShortAnswerSpot[];
    public solutions?: ShortAnswerSolution[];
    public correctMappings?: ShortAnswerMapping[];
    public matchLetterCase = false;
    public similarityValue = 85;

    constructor() {
        super(QuizQuestionType.SHORT_ANSWER);
    }
}
