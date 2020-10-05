import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { QuizStatisticCounter } from 'app/entities/quiz/quiz-statistic-counter.model';
import { MultipleChoiceQuestionStatistic } from 'app/entities/quiz/multiple-choice-question-statistic.model';

export class AnswerCounter extends QuizStatisticCounter {
    public answer?: AnswerOption;
    public multipleChoiceQuestionStatistic?: MultipleChoiceQuestionStatistic;

    constructor() {
        super();
    }
}
