import { QuestionStatistic } from '../question-statistic';
import { AnswerCounter } from '../answer-counter';

export class MultipleChoiceQuestionStatistic extends QuestionStatistic {

    public answerCounters: AnswerCounter[];

    constructor() {
        super();
    }
}
