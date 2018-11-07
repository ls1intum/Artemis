import { ShortAnswerSubmittedText } from '../short-answer-submitted-text';
import { SubmittedAnswer } from '../submitted-answer';
import { QuestionType } from 'app/entities/question';

export class ShortAnswerSubmittedAnswer extends SubmittedAnswer {
    public submittedTexts: ShortAnswerSubmittedText[];

    constructor() {
        super(QuestionType.SHORT_ANSWER);
    }
}
