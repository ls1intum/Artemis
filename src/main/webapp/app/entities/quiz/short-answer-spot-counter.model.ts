import { QuizStatisticCounter } from 'app/entities/quiz/quiz-statistic-counter.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerQuestionStatistic } from 'app/entities/quiz/short-answer-question-statistic.model';

export class ShortAnswerSpotCounter extends QuizStatisticCounter {
    public spot?: ShortAnswerSpot;
    public shortAnswerQuestionStatistic?: ShortAnswerQuestionStatistic;

    constructor() {
        super();
    }
}
