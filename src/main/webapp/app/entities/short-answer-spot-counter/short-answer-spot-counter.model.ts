import { QuizStatisticCounter } from 'app/entities/quiz-statistic-counter/quiz-statistic-counter.model';
import { ShortAnswerSpot } from 'app/entities/short-answer-spot/short-answer-spot.model';
import { ShortAnswerQuestionStatistic } from 'app/entities/short-answer-question-statistic/short-answer-question-statistic.model';

export class ShortAnswerSpotCounter extends QuizStatisticCounter {
    public spot: ShortAnswerSpot;
    public shortAnswerQuestionStatistic: ShortAnswerQuestionStatistic;

    constructor() {
        super();
    }
}
