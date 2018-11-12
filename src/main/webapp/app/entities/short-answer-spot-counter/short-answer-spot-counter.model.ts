import { ShortAnswerSpot } from '../short-answer-spot';
import { ShortAnswerQuestionStatistic } from '../short-answer-question-statistic';
import { StatisticCounter } from '../statistic-counter';

export class ShortAnswerSpotCounter extends StatisticCounter {
    public spot: ShortAnswerSpot;
    public shortAnswerQuestionStatistic: ShortAnswerQuestionStatistic;

    constructor() {
        super();
    }
}
