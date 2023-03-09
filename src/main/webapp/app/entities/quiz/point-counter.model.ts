import { QuizPointStatistic } from 'app/entities/quiz/quiz-point-statistic.model';
import { QuizStatisticCounter } from 'app/entities/quiz/quiz-statistic-counter.model';

export class PointCounter extends QuizStatisticCounter {
    public points?: number;
    public quizPointStatistic?: QuizPointStatistic;

    constructor() {
        super();
    }
}
