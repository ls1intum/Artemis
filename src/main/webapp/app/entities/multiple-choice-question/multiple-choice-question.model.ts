import { AnswerOption } from '../answer-option';
import { Question, QuestionType } from '../question';

export class MultipleChoiceQuestion extends Question {

    public answerOptions?: AnswerOption[];

    public hasCorrectOption: boolean;

    constructor() {
        super(QuestionType.MULTIPLE_CHOICE);
    }
}
