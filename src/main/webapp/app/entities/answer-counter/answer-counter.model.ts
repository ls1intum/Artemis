import { AnswerOption } from '../answer-option';
import { MultipleChoiceQuestionStatistic } from '../multiple-choice-question-statistic';
import { QuizStatisticCounter } from '../quiz-statistic-counter';

export class AnswerCounter extends QuizStatisticCounter {

    public answer: AnswerOption;
    public multipleChoiceQuestionStatistic: MultipleChoiceQuestionStatistic;

    constructor() {
        super();
    }
}
