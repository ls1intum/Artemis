import { QuizQuestionStatistic } from '../quiz-question-statistic';
import { AnswerCounter } from '../answer-counter';

export class MultipleChoiceQuestionStatistic extends QuizQuestionStatistic {

    public answerCounters: AnswerCounter[];

    constructor() {
        super();
    }
}
