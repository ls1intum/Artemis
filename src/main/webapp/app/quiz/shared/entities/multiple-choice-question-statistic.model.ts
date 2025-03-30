import { AnswerCounter } from 'app/quiz/shared/entities/answer-counter.model';
import { QuizQuestionStatistic } from 'app/quiz/shared/entities/quiz-question-statistic.model';

export class MultipleChoiceQuestionStatistic extends QuizQuestionStatistic {
    public answerCounters?: AnswerCounter[];

    constructor() {
        super();
    }
}
