import { ShortAnswerSpotCounter } from 'app/entities/short-answer-spot-counter/short-answer-spot-counter.model';
import { QuizQuestionStatistic } from 'app/entities/quiz-question-statistic/quiz-question-statistic.model';

export class ShortAnswerQuestionStatistic extends QuizQuestionStatistic {
    public shortAnswerSpotCounters: ShortAnswerSpotCounter[];

    constructor() {
        super();
    }
}
