import { IDropLocationCounter } from 'app/shared/model//drop-location-counter.model';

export interface IDragAndDropQuestionStatistic {
    id?: number;
    dropLocationCounters?: IDropLocationCounter[];
}

export class DragAndDropQuestionStatistic implements IDragAndDropQuestionStatistic {
    constructor(public id?: number, public dropLocationCounters?: IDropLocationCounter[]) {}
}
