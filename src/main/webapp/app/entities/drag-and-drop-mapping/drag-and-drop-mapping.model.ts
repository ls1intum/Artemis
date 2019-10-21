import { BaseEntity } from 'app/shared';
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
    public submittedAnswer: DragAndDropSubmittedAnswer;
    public question: DragAndDropQuestion;

    constructor(public dragItem: DragItem | null, public dropLocation: DropLocation | null) {}
}
