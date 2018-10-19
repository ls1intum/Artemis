import { IQuizPointStatistic } from 'app/shared/model//quiz-point-statistic.model';

export interface IPointCounter {
    id?: number;
    points?: number;
    quizPointStatistic?: IQuizPointStatistic;
}

export class PointCounter implements IPointCounter {
    constructor(public id?: number, public points?: number, public quizPointStatistic?: IQuizPointStatistic) {}
}
