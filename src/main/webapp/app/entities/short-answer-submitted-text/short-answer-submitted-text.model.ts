import { ShortAnswerSpot } from '../short-answer-spot';
import { ShortAnswerSubmittedAnswer } from '../short-answer-submitted-answer';
import { BaseEntity } from 'app/shared';

export class ShortAnswerSubmittedText implements BaseEntity {
    public id: number;
    public text: string;
    public isCorrect: string;
    public spot: ShortAnswerSpot;
    public submittedAnswer: ShortAnswerSubmittedAnswer;
}
