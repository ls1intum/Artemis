import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';

export class MultipleChoiceQuestion extends QuizQuestion {
    public answerOptions?: AnswerOption[];
    public hasCorrectOption?: boolean;
    public singleChoice?: boolean;

    constructor() {
        super(QuizQuestionType.MULTIPLE_CHOICE);
    }
}
