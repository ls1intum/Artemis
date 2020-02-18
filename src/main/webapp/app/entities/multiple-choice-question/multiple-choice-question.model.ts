import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz-question/quiz-question.model';
import { AnswerOption } from 'app/entities/answer-option/answer-option.model';

export class MultipleChoiceQuestion extends QuizQuestion {
    public answerOptions?: AnswerOption[];

    public hasCorrectOption: boolean | null;

    constructor() {
        super(QuizQuestionType.MULTIPLE_CHOICE);
    }
}
