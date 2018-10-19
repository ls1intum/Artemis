import { IAnswerOption } from 'app/shared/model//answer-option.model';

export interface IMultipleChoiceQuestion {
    id?: number;
    answerOptions?: IAnswerOption[];
}

export class MultipleChoiceQuestion implements IMultipleChoiceQuestion {
    constructor(public id?: number, public answerOptions?: IAnswerOption[]) {}
}
