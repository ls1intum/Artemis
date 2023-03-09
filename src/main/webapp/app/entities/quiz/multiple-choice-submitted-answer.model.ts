import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { SubmittedAnswer } from 'app/entities/quiz/submitted-answer.model';

export class MultipleChoiceSubmittedAnswer extends SubmittedAnswer {
    public selectedOptions?: AnswerOption[];

    constructor() {
        super(QuizQuestionType.MULTIPLE_CHOICE);
    }
}
