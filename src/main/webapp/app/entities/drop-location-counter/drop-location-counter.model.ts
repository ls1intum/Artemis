import { DropLocation } from '../drop-location';
import { DragAndDropQuestionStatistic } from '../drag-and-drop-question-statistic';
import { StatisticCounter } from '../statistic-counter';

export class DropLocationCounter extends StatisticCounter {

    public dropLocation: DropLocation;
    public dragAndDropQuestionStatistic: DragAndDropQuestionStatistic;

    constructor() {
        super();
    }
}
