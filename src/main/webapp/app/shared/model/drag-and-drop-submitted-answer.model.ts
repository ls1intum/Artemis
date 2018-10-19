import { IDragAndDropMapping } from 'app/shared/model//drag-and-drop-mapping.model';

export interface IDragAndDropSubmittedAnswer {
    id?: number;
    mappings?: IDragAndDropMapping[];
}

export class DragAndDropSubmittedAnswer implements IDragAndDropSubmittedAnswer {
    constructor(public id?: number, public mappings?: IDragAndDropMapping[]) {}
}
