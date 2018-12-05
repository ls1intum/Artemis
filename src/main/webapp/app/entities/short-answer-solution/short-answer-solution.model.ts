import { ShortAnswerQuestion } from '../short-answer-question';
import { BaseEntity } from 'app/shared';
import { MarkDownElement } from 'app/entities/question';

export class ShortAnswerSolution implements BaseEntity, MarkDownElement {
    public id: number;
    public text: string;
    public invalid = false; //default value
    public question: ShortAnswerQuestion;

    //additionally added after database changes with Stephan
    public posX: number;
    public posY: number;
    public tempID: number;

    //For MarkDownElement
    public hint: string;
    public explanation: string;

    constructor() {}
}
