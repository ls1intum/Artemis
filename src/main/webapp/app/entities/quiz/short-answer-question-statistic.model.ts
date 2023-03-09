import { QuizQuestionStatistic } from 'app/entities/quiz/quiz-question-statistic.model';
import { ShortAnswerSpotCounter } from 'app/entities/quiz/short-answer-spot-counter.model';

export class ShortAnswerQuestionStatistic extends QuizQuestionStatistic {
    public shortAnswerSpotCounters?: ShortAnswerSpotCounter[];

    constructor() {
        super();
    }
}
