import { IDropLocation } from 'app/shared/model//drop-location.model';
import { IDragItem } from 'app/shared/model//drag-item.model';
import { IDragAndDropMapping } from 'app/shared/model//drag-and-drop-mapping.model';

export interface IDragAndDropQuestion {
    id?: number;
    backgroundFilePath?: string;
    dropLocations?: IDropLocation[];
    dragItems?: IDragItem[];
    correctMappings?: IDragAndDropMapping[];
}

export class DragAndDropQuestion implements IDragAndDropQuestion {
    constructor(
        public id?: number,
        public backgroundFilePath?: string,
        public dropLocations?: IDropLocation[],
        public dragItems?: IDragItem[],
        public correctMappings?: IDragAndDropMapping[]
    ) {}
}
