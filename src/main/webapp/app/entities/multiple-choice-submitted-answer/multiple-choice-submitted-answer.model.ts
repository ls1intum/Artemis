import { SubmittedAnswer } from '../submitted-answer';
import { AnswerOption } from '../answer-option';

export class MultipleChoiceSubmittedAnswer extends SubmittedAnswer {
    constructor(
        public id?: number,
        public selectedOptions?: AnswerOption[],
    ) {
        super();
    }
}
