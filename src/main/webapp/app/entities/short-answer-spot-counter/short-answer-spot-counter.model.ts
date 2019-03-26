import { ShortAnswerSpot } from '../short-answer-spot';
import { ShortAnswerQuestionStatistic } from '../short-answer-question-statistic';
import { QuizStatisticCounter } from '../quiz-statistic-counter';

export class ShortAnswerSpotCounter extends QuizStatisticCounter {
    public spot: ShortAnswerSpot;
    public shortAnswerQuestionStatistic: ShortAnswerQuestionStatistic;

    constructor() {
        super();
    }
}
