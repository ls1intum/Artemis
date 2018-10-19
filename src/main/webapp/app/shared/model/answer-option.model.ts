import { IMultipleChoiceQuestion } from 'app/shared/model//multiple-choice-question.model';

export interface IAnswerOption {
    id?: number;
    text?: string;
    hint?: string;
    explanation?: string;
    isCorrect?: boolean;
    invalid?: boolean;
    question?: IMultipleChoiceQuestion;
}

export class AnswerOption implements IAnswerOption {
    constructor(
        public id?: number,
        public text?: string,
        public hint?: string,
        public explanation?: string,
        public isCorrect?: boolean,
        public invalid?: boolean,
        public question?: IMultipleChoiceQuestion
    ) {
        this.isCorrect = this.isCorrect || false;
        this.invalid = this.invalid || false;
    }
}
