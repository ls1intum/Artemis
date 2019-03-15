import { QuizStatisticCounter } from '../quiz-statistic-counter';
import { QuizPointStatistic } from '../quiz-point-statistic';

export class PointCounter extends QuizStatisticCounter {

    public points: number;
    public quizPointStatistic: QuizPointStatistic;
    public ratedCounter: number;
    public unRatedCounter: number;

    constructor() {
        super();
    }
}
