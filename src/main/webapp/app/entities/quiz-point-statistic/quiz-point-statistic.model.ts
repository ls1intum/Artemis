import { PointCounter } from 'app/entities/point-counter/point-counter.model';
import { QuizStatistic } from 'app/entities/quiz-statistic/quiz-statistic.model';
import { QuizExercise } from 'app/entities/quiz-exercise/quiz-exercise.model';

export class QuizPointStatistic extends QuizStatistic {
    public pointCounters: PointCounter[];
    public quiz: QuizExercise;

    constructor() {
        super();
    }
}
