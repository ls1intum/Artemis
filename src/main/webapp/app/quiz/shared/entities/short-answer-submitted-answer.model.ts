import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';

export class ShortAnswerSubmittedAnswer extends SubmittedAnswer {
    public submittedTexts?: ShortAnswerSubmittedText[];

    constructor() {
        super(QuizQuestionType.SHORT_ANSWER);
    }
}
