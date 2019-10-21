import { ShortAnswerSpotCounter } from '../short-answer-spot-counter';
import { QuizQuestionStatistic } from '../quiz-question-statistic';

export class ShortAnswerQuestionStatistic extends QuizQuestionStatistic {
    public shortAnswerSpotCounters: ShortAnswerSpotCounter[];

    constructor() {
        super();
    }
}
