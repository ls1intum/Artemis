import { AnswerOption } from '../answer-option';
import { QuizQuestion, QuizQuestionType } from '../quiz-question';

export class MultipleChoiceQuestion extends QuizQuestion {

    public answerOptions?: AnswerOption[];

    public hasCorrectOption: boolean;

    constructor() {
        super(QuizQuestionType.MULTIPLE_CHOICE);
    }
}
