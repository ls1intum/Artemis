import { PointCounter } from 'app/entities/quiz/point-counter.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizStatistic } from 'app/entities/quiz/quiz-statistic.model';

export class QuizPointStatistic extends QuizStatistic {
    public pointCounters?: PointCounter[];
    public quiz?: QuizExercise;

    constructor() {
        super();
    }
}
