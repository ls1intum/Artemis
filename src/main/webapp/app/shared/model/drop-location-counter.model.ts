import { IDropLocation } from 'app/shared/model//drop-location.model';
import { IDragAndDropQuestionStatistic } from 'app/shared/model//drag-and-drop-question-statistic.model';

export interface IDropLocationCounter {
    id?: number;
    dropLocation?: IDropLocation;
    dragAndDropQuestionStatistic?: IDragAndDropQuestionStatistic;
}

export class DropLocationCounter implements IDropLocationCounter {
    constructor(
        public id?: number,
        public dropLocation?: IDropLocation,
        public dragAndDropQuestionStatistic?: IDragAndDropQuestionStatistic
    ) {}
}
