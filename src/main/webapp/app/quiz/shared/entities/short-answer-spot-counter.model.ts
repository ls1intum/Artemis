import { QuizStatisticCounter } from 'app/quiz/shared/entities/quiz-statistic-counter.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';
import { ShortAnswerQuestionStatistic } from 'app/quiz/shared/entities/short-answer-question-statistic.model';

export class ShortAnswerSpotCounter extends QuizStatisticCounter {
    public spot?: ShortAnswerSpot;
    public shortAnswerQuestionStatistic?: ShortAnswerQuestionStatistic;

    constructor() {
        super();
    }
}
