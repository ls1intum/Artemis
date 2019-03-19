import { SubmittedAnswer } from '../submitted-answer';
import { AnswerOption } from '../answer-option';
import { QuizQuestionType } from '../quiz-question';

export class MultipleChoiceSubmittedAnswer extends SubmittedAnswer {

    public selectedOptions: AnswerOption[];

    constructor() {
        super(QuizQuestionType.MULTIPLE_CHOICE);
    }
}
