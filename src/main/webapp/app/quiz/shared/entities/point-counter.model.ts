import { QuizStatisticCounter } from 'app/quiz/shared/entities/quiz-statistic-counter.model';
import { QuizPointStatistic } from 'app/quiz/shared/entities/quiz-point-statistic.model';

export class PointCounter extends QuizStatisticCounter {
    public points?: number;
    public quizPointStatistic?: QuizPointStatistic;

    constructor() {
        super();
    }
}
