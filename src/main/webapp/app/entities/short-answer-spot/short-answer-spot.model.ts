import { ShortAnswerQuestion } from '../short-answer-question';
import { BaseEntity } from 'app/shared';

export class ShortAnswerSpot implements BaseEntity {
    public id: number;
    public spotNr: number; //added after database changes with Stephan
    public width: number;
    public spotNr: number;
    public invalid = false; // default
    public question: ShortAnswerQuestion;
    public tempID: number;

    //additionally added after database changes with Stephan
    public posX: number;
    public posY: number;

    constructor() {}
}
