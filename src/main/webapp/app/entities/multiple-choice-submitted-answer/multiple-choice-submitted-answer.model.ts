import { SubmittedAnswer } from 'app/entities/submitted-answer/submitted-answer.model';
import { QuizQuestionType } from 'app/entities/quiz-question/quiz-question.model';
import { AnswerOption } from 'app/entities/answer-option/answer-option.model';

export class MultipleChoiceSubmittedAnswer extends SubmittedAnswer {
    public selectedOptions: AnswerOption[];

    constructor() {
        super(QuizQuestionType.MULTIPLE_CHOICE);
    }
}
