import { DropLocation } from '../drop-location';
import { DragAndDropQuestionStatistic } from '../drag-and-drop-question-statistic';
import { StatisticCounter } from 'app/entities/statistic-counter';

export class DropLocationCounter extends StatisticCounter {
    constructor(
        public id?: number,
        public dropLocation?: DropLocation,
        public dragAndDropQuestionStatistic?: DragAndDropQuestionStatistic,
    ) {
        super();
    }
}
