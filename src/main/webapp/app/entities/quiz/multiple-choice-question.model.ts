import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';

export class MultipleChoiceQuestion extends QuizQuestion {
    public answerOptions?: AnswerOption[];
    public hasCorrectOption?: boolean;
    public singleChoice?: boolean;

    constructor() {
        super(QuizQuestionType.MULTIPLE_CHOICE);
    }
}
