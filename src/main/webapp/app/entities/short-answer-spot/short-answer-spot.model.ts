import { ShortAnswerQuestion } from '../short-answer-question';
import { BaseEntity } from 'app/shared';

export class ShortAnswerSpot implements BaseEntity {
    public id: number;
    public width: number;
    public invalid = false; // default
    public question: ShortAnswerQuestion;

    //additionally added after database changes with Stephan
    public posX: number;
    public posY: number;
    public tempID: number;

    constructor() {}
}
