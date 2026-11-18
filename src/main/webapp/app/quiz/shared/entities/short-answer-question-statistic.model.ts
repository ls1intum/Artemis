import { ShortAnswerSpotCounter } from 'app/quiz/shared/entities/short-answer-spot-counter.model';
import { QuizQuestionStatistic } from 'app/quiz/shared/entities/quiz-question-statistic.model';

export class ShortAnswerQuestionStatistic extends QuizQuestionStatistic {
    public shortAnswerSpotCounters?: ShortAnswerSpotCounter[];

    constructor() {
        super();
    }
}
