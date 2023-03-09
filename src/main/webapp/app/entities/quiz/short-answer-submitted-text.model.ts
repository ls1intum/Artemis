import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerSubmittedAnswer } from 'app/entities/quiz/short-answer-submitted-answer.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class ShortAnswerSubmittedText implements BaseEntity {
    public id?: number;
    public text?: string;
    public isCorrect?: boolean;
    public spot?: ShortAnswerSpot;
    public submittedAnswer?: ShortAnswerSubmittedAnswer;
}
