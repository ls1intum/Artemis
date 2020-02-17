import { BaseEntity } from 'app/shared/model/base-entity';
import { ShortAnswerSubmittedAnswer } from 'app/entities/short-answer-submitted-answer/short-answer-submitted-answer.model';
import { ShortAnswerSpot } from 'app/entities/short-answer-spot/short-answer-spot.model';

export class ShortAnswerSubmittedText implements BaseEntity {
    public id: number;
    public text: string;
    public isCorrect: boolean;
    public spot: ShortAnswerSpot;
    public submittedAnswer: ShortAnswerSubmittedAnswer;
}
