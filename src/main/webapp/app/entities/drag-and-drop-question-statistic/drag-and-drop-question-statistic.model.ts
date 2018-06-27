import { DropLocationCounter } from '../drop-location-counter';
import { QuestionStatistic } from '../question-statistic';

export class DragAndDropQuestionStatistic extends QuestionStatistic {
    constructor(
        public id?: number,
        public dropLocationCounters?: DropLocationCounter[],
    ) {
        super();
    }
}
