import { IPointCounter } from 'app/shared/model//point-counter.model';
import { IQuizExercise } from 'app/shared/model//quiz-exercise.model';

export interface IQuizPointStatistic {
    id?: number;
    pointCounters?: IPointCounter[];
    quiz?: IQuizExercise;
}

export class QuizPointStatistic implements IQuizPointStatistic {
    constructor(public id?: number, public pointCounters?: IPointCounter[], public quiz?: IQuizExercise) {}
}
