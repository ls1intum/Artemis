import { IAnswerOption } from 'app/shared/model//answer-option.model';

export interface IMultipleChoiceSubmittedAnswer {
    id?: number;
    selectedOptions?: IAnswerOption[];
}

export class MultipleChoiceSubmittedAnswer implements IMultipleChoiceSubmittedAnswer {
    constructor(public id?: number, public selectedOptions?: IAnswerOption[]) {}
}
