import { BaseEntity } from 'app/shared/model/base-entity';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';

export class ShortAnswerSubmittedText implements BaseEntity {
    public id?: number;
    public text?: string;
    public isCorrect?: boolean;
    public spot?: ShortAnswerSpot;
    public submittedAnswer?: ShortAnswerSubmittedAnswer;
}
