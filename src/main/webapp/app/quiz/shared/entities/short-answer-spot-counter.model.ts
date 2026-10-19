import { QuizStatisticCounter } from 'app/quiz/shared/entities/quiz-statistic-counter.model';
import { ShortAnswerQuestionStatistic } from 'app/quiz/shared/entities/short-answer-question-statistic.model';

export class ShortAnswerSpotCounter extends QuizStatisticCounter {
    // The counter's spot is referenced by its question-scoped id (matches the server's scalar `spotId` on both the REST DTO and the statistics websocket).
    public spotId?: number;
    public shortAnswerQuestionStatistic?: ShortAnswerQuestionStatistic;

    constructor() {
        super();
    }
}
