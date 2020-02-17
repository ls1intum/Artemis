import { AnswerOption } from 'app/entities/answer-option/answer-option.model';
import { QuizStatisticCounter } from 'app/entities/quiz-statistic-counter/quiz-statistic-counter.model';
import { MultipleChoiceQuestionStatistic } from 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic.model';

export class AnswerCounter extends QuizStatisticCounter {
    public answer: AnswerOption;
    public multipleChoiceQuestionStatistic: MultipleChoiceQuestionStatistic;

    constructor() {
        super();
    }
}
