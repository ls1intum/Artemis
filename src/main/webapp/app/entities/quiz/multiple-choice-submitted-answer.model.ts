import { SubmittedAnswer } from 'app/entities/quiz/submitted-answer.model';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';

export class MultipleChoiceSubmittedAnswer extends SubmittedAnswer {
    public selectedOptions?: AnswerOption[];

    constructor() {
        super(QuizQuestionType.MULTIPLE_CHOICE);
    }
}
