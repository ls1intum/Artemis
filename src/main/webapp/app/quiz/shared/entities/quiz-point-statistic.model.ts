import { PointCounter } from 'app/quiz/shared/entities/point-counter.model';
import { QuizStatistic } from 'app/quiz/shared/entities/quiz-statistic.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';

export class QuizPointStatistic extends QuizStatistic {
    public pointCounters?: PointCounter[];
    public quiz?: QuizExercise;

    constructor() {
        super();
    }
}
