import { SubmittedAnswer } from 'app/entities/quiz/submitted-answer.model';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';

export class ShortAnswerSubmittedAnswer extends SubmittedAnswer {
    public submittedTexts: ShortAnswerSubmittedText[];

    constructor() {
        super(QuizQuestionType.SHORT_ANSWER);
    }
}
