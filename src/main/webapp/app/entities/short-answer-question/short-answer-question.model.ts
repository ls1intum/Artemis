import { ShortAnswerSpot } from '../short-answer-spot';
import { ShortAnswerSolution } from '../short-answer-solution';
import { ShortAnswerMapping } from '../short-answer-mapping';
import { Question, QuestionType } from 'app/entities/question';

export class ShortAnswerQuestion extends Question {
    public spots: ShortAnswerSpot[];
    public solutions: ShortAnswerSolution[];
    public correctMappings: ShortAnswerMapping[];

    constructor() {
        super(QuestionType.SHORT_ANSWER);
    }
}
