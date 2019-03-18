import { ShortAnswerSpot } from '../short-answer-spot';
import { ShortAnswerSolution } from '../short-answer-solution';
import { ShortAnswerMapping } from '../short-answer-mapping';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz-question';

export class ShortAnswerQuestion extends QuizQuestion {
    public spots: ShortAnswerSpot[];
    public solutions: ShortAnswerSolution[];
    public correctMappings: ShortAnswerMapping[];

    constructor() {
        super(QuizQuestionType.SHORT_ANSWER);
    }
}
