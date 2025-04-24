import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';

export class MultipleChoiceSubmittedAnswer extends SubmittedAnswer {
    public selectedOptions?: AnswerOption[];

    constructor() {
        super(QuizQuestionType.MULTIPLE_CHOICE);
    }
}
