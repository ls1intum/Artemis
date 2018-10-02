import { BaseEntity } from './../../shared';
import { DragItem } from '../drag-item';
import { DropLocation } from '../drop-location';
import { DragAndDropSubmittedAnswer } from '../drag-and-drop-submitted-answer';
import { DragAndDropQuestion } from '../drag-and-drop-question';

export class DragAndDropMapping implements BaseEntity {

    public id: number;
    public tempID: number;
    public dragItemIndex: number;
    public dropLocationIndex: number;
    public invalid = false; // default value
    public dragItem: DragItem;
    public dropLocation: DropLocation;
    public submittedAnswer: DragAndDropSubmittedAnswer;
    public question: DragAndDropQuestion;

    constructor(dragItem?: DragItem, dropLocation?: DropLocation) {
        this.dragItem = dragItem;
        this.dropLocation = dropLocation;
    }
}
