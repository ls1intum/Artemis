import { StatisticCounter } from '../statistic-counter';
import { QuizPointStatistic } from '../quiz-point-statistic';

export class PointCounter extends StatisticCounter {
    constructor(
        public id?: number,
        public points?: number,
        public quizPointStatistic?: QuizPointStatistic,
        public ratedCounter?: number,
        public unRatedCounter?: number,
    ) {
        super();
    }
}
