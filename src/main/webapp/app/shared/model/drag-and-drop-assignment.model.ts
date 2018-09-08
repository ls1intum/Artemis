import { IDragItem } from 'app/shared/model/drag-item.model';
import { IDropLocation } from 'app/shared/model/drop-location.model';
import { IDragAndDropSubmittedAnswer } from 'app/shared/model/drag-and-drop-submitted-answer.model';

export interface IDragAndDropAssignment {
    id?: number;
    item?: IDragItem;
    location?: IDropLocation;
    submittedAnswer?: IDragAndDropSubmittedAnswer;
}

export class DragAndDropAssignment implements IDragAndDropAssignment {
    constructor(
        public id?: number,
        public item?: IDragItem,
        public location?: IDropLocation,
        public submittedAnswer?: IDragAndDropSubmittedAnswer
    ) {}
}
