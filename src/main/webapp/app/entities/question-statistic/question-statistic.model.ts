import { Statistic } from '../statistic';
import { Question } from '../question';

export class QuestionStatistic extends Statistic {

    public ratedCorrectCounter: number;
    public unRatedCorrectCounter: number;
    public question: Question;

    constructor() {
        super();
    }
}
