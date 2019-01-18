import { ShortAnswerQuestion } from '../short-answer-question';
import { BaseEntity } from 'app/shared';

export class ShortAnswerSolution implements BaseEntity {
    public id: number;
    public text: string;
    public invalid = false; // default value
    public question: ShortAnswerQuestion;

    constructor() {
    }
}
