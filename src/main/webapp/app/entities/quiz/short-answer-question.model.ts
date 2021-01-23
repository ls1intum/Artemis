import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';

export class ShortAnswerQuestion extends QuizQuestion {
    public spots?: ShortAnswerSpot[];
    public solutions?: ShortAnswerSolution[];
    public correctMappings?: ShortAnswerMapping[];
    public matchLetterCase: Boolean = false;
    public similarityValue: Number = 85;

    constructor() {
        super(QuizQuestionType.SHORT_ANSWER);
    }
}
