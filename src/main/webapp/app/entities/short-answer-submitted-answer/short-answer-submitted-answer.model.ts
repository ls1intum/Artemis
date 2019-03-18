import { ShortAnswerSubmittedText } from '../short-answer-submitted-text';
import { SubmittedAnswer } from '../submitted-answer';
import { QuizQuestionType } from 'app/entities/quiz-question';

export class ShortAnswerSubmittedAnswer extends SubmittedAnswer {
    public submittedTexts: ShortAnswerSubmittedText[];

    constructor() {
        super(QuizQuestionType.SHORT_ANSWER);
    }
}
