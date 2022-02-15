import { BaseEntity } from 'app/shared/model/base-entity';
import { DragAndDropSubmittedAnswer } from 'app/entities/quiz/drag-and-drop-submitted-answer.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { CanBecomeInvalid, DropLocation } from 'app/entities/quiz/drop-location.model';

export class DragAndDropMapping implements BaseEntity, CanBecomeInvalid {
    public id?: number;
    public dragItemIndex?: number;
    public dropLocationIndex?: number;
    public invalid = false;
    public submittedAnswer?: DragAndDropSubmittedAnswer;
    public question?: DragAndDropQuestion;
    public dragItem?: DragItem;
    public dropLocation?: DropLocation;

    constructor(dragItem: DragItem | undefined, dropLocation: DropLocation | undefined) {
        this.dragItem = dragItem;
        this.dropLocation = dropLocation;
    }
}
