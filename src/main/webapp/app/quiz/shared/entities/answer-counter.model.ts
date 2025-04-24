import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { QuizStatisticCounter } from 'app/quiz/shared/entities/quiz-statistic-counter.model';
import { MultipleChoiceQuestionStatistic } from 'app/quiz/shared/entities/multiple-choice-question-statistic.model';

export class AnswerCounter extends QuizStatisticCounter {
    public answer?: AnswerOption;
    public multipleChoiceQuestionStatistic?: MultipleChoiceQuestionStatistic;

    constructor() {
        super();
    }
}
