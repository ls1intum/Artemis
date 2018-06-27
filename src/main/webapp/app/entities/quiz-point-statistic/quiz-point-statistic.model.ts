import { PointCounter } from '../point-counter';
import { Statistic } from '../statistic';
import { QuizExercise } from '../quiz-exercise';

export class QuizPointStatistic extends Statistic {
    constructor(
        public id?: number,
        public pointCounters?: PointCounter[],
        public quiz?: QuizExercise,
        public released?: boolean,
        public participantsUnrated?: number,
        public participantsRated?: number,
    ) {
        super();
    }
}
