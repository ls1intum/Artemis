import { QuestionStatistic } from '../question-statistic';
import { AnswerCounter } from '../answer-counter';

export class MultipleChoiceQuestionStatistic extends QuestionStatistic {
    constructor(
        public id?: number,
        public answerCounters?: AnswerCounter[],
    ) {
        super();
    }
}
