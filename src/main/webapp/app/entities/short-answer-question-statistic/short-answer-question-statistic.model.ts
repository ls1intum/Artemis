import { ShortAnswerSpotCounter } from '../short-answer-spot-counter';
import { QuestionStatistic } from '../question-statistic';

export class ShortAnswerQuestionStatistic extends QuestionStatistic {
    public shortAnswerSpotCounters: ShortAnswerSpotCounter[];

    constructor() {
        super();
    }
}
