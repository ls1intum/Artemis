import { AnswerOption } from 'app/entities/answer-option';
import { Question } from '../question';

export class MultipleChoiceQuestion extends Question {
    constructor(
        public id?: number,
        public answerOptions?: AnswerOption[],
    ) {
        super();
    }
}
