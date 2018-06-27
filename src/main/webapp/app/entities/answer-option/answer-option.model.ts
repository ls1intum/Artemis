import { BaseEntity } from './../../shared';
import { MultipleChoiceQuestion } from '../multiple-choice-question';

export class AnswerOption implements BaseEntity {
    constructor(
        public id?: number,
        public text?: string,
        public hint?: string,
        public explanation?: string,
        public isCorrect?: boolean,
        public question?: MultipleChoiceQuestion,
        public invalid?: boolean,
    ) {
        this.isCorrect = false;
    }
}
