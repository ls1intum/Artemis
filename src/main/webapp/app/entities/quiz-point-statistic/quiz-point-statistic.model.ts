import { PointCounter } from '../point-counter';
import { QuizStatistic } from '../quiz-statistic';
import { QuizExercise } from '../quiz-exercise';

export class QuizPointStatistic extends QuizStatistic {

    public pointCounters: PointCounter[];
    public quiz: QuizExercise;

    constructor() {
        super();
    }
}
