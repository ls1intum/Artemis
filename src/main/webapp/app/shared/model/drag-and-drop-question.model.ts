import { IDropLocation } from 'app/shared/model/drop-location.model';
import { IDragItem } from 'app/shared/model/drag-item.model';

export interface IDragAndDropQuestion {
    id?: number;
    backgroundFilePath?: string;
    dropLocations?: IDropLocation[];
    dragItems?: IDragItem[];
}

export class DragAndDropQuestion implements IDragAndDropQuestion {
    constructor(
        public id?: number,
        public backgroundFilePath?: string,
        public dropLocations?: IDropLocation[],
        public dragItems?: IDragItem[]
    ) {}
}
