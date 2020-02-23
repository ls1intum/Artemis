import { QuizStatisticCounter } from 'app/entities/quiz/quiz-statistic-counter.model';
import { QuizPointStatistic } from 'app/entities/quiz/quiz-point-statistic.model';

export class PointCounter extends QuizStatisticCounter {
    public points: number;
    public quizPointStatistic: QuizPointStatistic;
    public ratedCounter: number;
    public unRatedCounter: number;

    constructor() {
        super();
    }
}
