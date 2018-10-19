import { IDragItem } from 'app/shared/model//drag-item.model';
import { IDropLocation } from 'app/shared/model//drop-location.model';
import { IDragAndDropSubmittedAnswer } from 'app/shared/model//drag-and-drop-submitted-answer.model';
import { IDragAndDropQuestion } from 'app/shared/model//drag-and-drop-question.model';

export interface IDragAndDropMapping {
    id?: number;
    dragItemIndex?: number;
    dropLocationIndex?: number;
    invalid?: boolean;
    dragItem?: IDragItem;
    dropLocation?: IDropLocation;
    submittedAnswer?: IDragAndDropSubmittedAnswer;
    question?: IDragAndDropQuestion;
}

export class DragAndDropMapping implements IDragAndDropMapping {
    constructor(
        public id?: number,
        public dragItemIndex?: number,
        public dropLocationIndex?: number,
        public invalid?: boolean,
        public dragItem?: IDragItem,
        public dropLocation?: IDropLocation,
        public submittedAnswer?: IDragAndDropSubmittedAnswer,
        public question?: IDragAndDropQuestion
    ) {
        this.invalid = this.invalid || false;
    }
}
