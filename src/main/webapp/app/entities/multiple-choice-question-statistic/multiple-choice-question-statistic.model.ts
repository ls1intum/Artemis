import { AnswerCounter } from 'app/entities/answer-counter/answer-counter.model';
import { QuizQuestionStatistic } from 'app/entities/quiz-question-statistic/quiz-question-statistic.model';

export class MultipleChoiceQuestionStatistic extends QuizQuestionStatistic {
    public answerCounters: AnswerCounter[];

    constructor() {
        super();
    }
}
