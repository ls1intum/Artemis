import { SubmittedAnswer } from 'app/entities/submitted-answer/submitted-answer.model';
import { QuizQuestionType } from 'app/entities/quiz-question/quiz-question.model';
import { ShortAnswerSubmittedText } from 'app/entities/short-answer-submitted-text/short-answer-submitted-text.model';

export class ShortAnswerSubmittedAnswer extends SubmittedAnswer {
    public submittedTexts: ShortAnswerSubmittedText[];

    constructor() {
        super(QuizQuestionType.SHORT_ANSWER);
    }
}
