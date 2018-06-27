import { Statistic } from '../statistic';
import { Question } from '../question';

export class QuestionStatistic extends Statistic {
    constructor(
        public id?: number,
        public ratedCorrectCounter?: number,
        public unRatedCorrectCounter?: number,
        public question?: Question,
        public released?: boolean,
        public participantsRated?: number,
        public participantsUnrated?: number,
    ) {
        super();
    }
}
