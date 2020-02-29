import { AnswerCounter } from 'app/entities/quiz/answer-counter.model';
import { QuizQuestionStatistic } from 'app/entities/quiz/quiz-question-statistic.model';

export class MultipleChoiceQuestionStatistic extends QuizQuestionStatistic {
    public answerCounters: AnswerCounter[];

    constructor() {
        super();
    }
}
