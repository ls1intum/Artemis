import { ShortAnswerQuestion } from '../short-answer-question';
import { BaseEntity } from 'app/shared';

export class ShortAnswerSpot implements BaseEntity {
    public id: number;
    public width: number;
    public invalid = false; // default
    public question: ShortAnswerQuestion;

    constructor() {}
}
