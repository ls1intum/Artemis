import { BaseEntity } from 'app/shared';
import { MultipleChoiceQuestion } from '../multiple-choice-question';
import { MarkDownElement } from '../quiz-question';

export class AnswerOption implements BaseEntity, MarkDownElement {
    public id: number;
    public text: string | null;
    public hint: string | null;
    public explanation: string | null;
    public isCorrect = false; // default value
    public question: MultipleChoiceQuestion;
    public invalid = false; // default value

    constructor() {}
}
