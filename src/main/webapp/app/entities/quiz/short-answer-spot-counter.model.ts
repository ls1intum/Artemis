import { QuizStatisticCounter } from 'app/entities/quiz/quiz-statistic-counter.model';
import { ShortAnswerQuestionStatistic } from 'app/entities/quiz/short-answer-question-statistic.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';

export class ShortAnswerSpotCounter extends QuizStatisticCounter {
    public spot?: ShortAnswerSpot;
    public shortAnswerQuestionStatistic?: ShortAnswerQuestionStatistic;

    constructor() {
        super();
    }
}
