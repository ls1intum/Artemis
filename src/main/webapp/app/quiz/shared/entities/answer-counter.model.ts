import { QuizStatisticCounter } from 'app/quiz/shared/entities/quiz-statistic-counter.model';
import { MultipleChoiceQuestionStatistic } from 'app/quiz/shared/entities/multiple-choice-question-statistic.model';

export class AnswerCounter extends QuizStatisticCounter {
    // The counter's answer option is referenced by its question-scoped id (matches the server's scalar `answerId` on both the REST DTO and the statistics websocket).
    public answerId?: number;
    public multipleChoiceQuestionStatistic?: MultipleChoiceQuestionStatistic;

    constructor() {
        super();
    }
}
