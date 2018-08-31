import { PointCounter } from '../point-counter';
import { Statistic } from '../statistic';
import { QuizExercise } from '../quiz-exercise';

export class QuizPointStatistic extends Statistic {

    public pointCounters: PointCounter[];
    public quiz: QuizExercise;

    constructor() {
        super();
    }
}
