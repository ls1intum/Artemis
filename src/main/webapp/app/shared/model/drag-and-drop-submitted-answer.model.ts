import { IDragAndDropAssignment } from 'app/shared/model/drag-and-drop-assignment.model';

export interface IDragAndDropSubmittedAnswer {
    id?: number;
    assignments?: IDragAndDropAssignment[];
}

export class DragAndDropSubmittedAnswer implements IDragAndDropSubmittedAnswer {
    constructor(public id?: number, public assignments?: IDragAndDropAssignment[]) {}
}
