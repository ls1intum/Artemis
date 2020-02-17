import { BaseEntity } from 'app/shared/model/base-entity';
import { DragAndDropSubmittedAnswer } from 'app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer.model';
import { DragAndDropQuestion } from 'app/entities/drag-and-drop-question/drag-and-drop-question.model';
import { DragItem } from 'app/entities/drag-item/drag-item.model';
import { DropLocation } from 'app/entities/drop-location/drop-location.model';

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
