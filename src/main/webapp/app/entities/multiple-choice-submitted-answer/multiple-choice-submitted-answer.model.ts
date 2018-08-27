import { SubmittedAnswer } from '../submitted-answer';
import { AnswerOption } from '../answer-option';
import { QuestionType } from '../question';

export class MultipleChoiceSubmittedAnswer extends SubmittedAnswer {

    public selectedOptions: AnswerOption[];

    constructor() {
        super(QuestionType.MULTIPLE_CHOICE);
    }
}
