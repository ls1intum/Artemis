import { ShortAnswerQuestion } from '../short-answer-question';
import { BaseEntity } from 'app/shared';
import { generate } from 'app/quiz/edit/temp-id';

export class ShortAnswerSpot implements BaseEntity {
    public id: number;
    public width: number;
    public spotNr: number;
    public invalid = false; // default
    public question: ShortAnswerQuestion;
    public tempID: number;

    // additionally added after database changes with Stephan
    public posX: number;
    public posY: number;

    constructor() {
        this.tempID = generate();
    }
}
