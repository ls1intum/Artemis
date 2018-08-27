import { BaseEntity } from './../../shared';
import { MultipleChoiceQuestion } from '../multiple-choice-question';

export class AnswerOption implements BaseEntity {

    public id: number;
    public text: string;
    public hint: string;
    public explanation: string;
    public isCorrect = false;       // default value
    public question: MultipleChoiceQuestion;
    public invalid = false;         // default value

    constructor() {
    }
}
