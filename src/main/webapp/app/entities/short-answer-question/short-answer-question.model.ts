import { ShortAnswerMapping } from 'app/entities/short-answer-mapping/short-answer-mapping.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz-question/quiz-question.model';
import { ShortAnswerSpot } from 'app/entities/short-answer-spot/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/entities/short-answer-solution/short-answer-solution.model';

export class ShortAnswerQuestion extends QuizQuestion {
    public spots: ShortAnswerSpot[];
    public solutions: ShortAnswerSolution[];
    public correctMappings: ShortAnswerMapping[];

    constructor() {
        super(QuizQuestionType.SHORT_ANSWER);
    }
}
