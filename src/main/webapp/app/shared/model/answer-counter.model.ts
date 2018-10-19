import { IAnswerOption } from 'app/shared/model//answer-option.model';
import { IMultipleChoiceQuestionStatistic } from 'app/shared/model//multiple-choice-question-statistic.model';

export interface IAnswerCounter {
    id?: number;
    answer?: IAnswerOption;
    multipleChoiceQuestionStatistic?: IMultipleChoiceQuestionStatistic;
}

export class AnswerCounter implements IAnswerCounter {
    constructor(
        public id?: number,
        public answer?: IAnswerOption,
        public multipleChoiceQuestionStatistic?: IMultipleChoiceQuestionStatistic
    ) {}
}
