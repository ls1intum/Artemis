import { BaseEntity } from 'app/shared';
import { MultipleChoiceQuestion } from '../multiple-choice-question';
import { MarkDownElement } from '../../entities/question';

export class AnswerOption implements BaseEntity, MarkDownElement {
    public id: number;
    public text: string;
    public hint: string;
    public explanation: string;
    public isCorrect = false; // default value
    public question: MultipleChoiceQuestion;
    public invalid = false; // default value

    constructor() {}
}
