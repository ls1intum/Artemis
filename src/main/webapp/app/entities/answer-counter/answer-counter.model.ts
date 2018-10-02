import { AnswerOption } from '../answer-option';
import { MultipleChoiceQuestionStatistic } from '../multiple-choice-question-statistic';
import { StatisticCounter } from '../statistic-counter';

export class AnswerCounter extends StatisticCounter {

    public answer: AnswerOption;
    public multipleChoiceQuestionStatistic: MultipleChoiceQuestionStatistic;

    constructor() {
        super();
    }
}
