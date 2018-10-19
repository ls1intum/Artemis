import { IAnswerCounter } from 'app/shared/model//answer-counter.model';

export interface IMultipleChoiceQuestionStatistic {
    id?: number;
    answerCounters?: IAnswerCounter[];
}

export class MultipleChoiceQuestionStatistic implements IMultipleChoiceQuestionStatistic {
    constructor(public id?: number, public answerCounters?: IAnswerCounter[]) {}
}
