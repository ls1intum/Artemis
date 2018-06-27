import { BaseEntity } from './../../shared';
import { DragItem } from '../drag-item';
import { DropLocation } from '../drop-location';
import { DragAndDropSubmittedAnswer } from '../drag-and-drop-submitted-answer';
import { DragAndDropQuestion } from '../drag-and-drop-question';

export class DragAndDropMapping implements BaseEntity {
    constructor(
        public id?: number,
        public dragItemIndex?: number,
        public dropLocationIndex?: number,
        public invalid?: boolean,
        public dragItem?: DragItem,
        public dropLocation?: DropLocation,
        public submittedAnswer?: DragAndDropSubmittedAnswer,
        public question?: DragAndDropQuestion,
    ) {
        this.invalid = false;
    }
}
