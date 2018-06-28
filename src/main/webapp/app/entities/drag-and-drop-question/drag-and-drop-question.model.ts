import { Question } from '../question';
import { DropLocation } from '../drop-location';
import { DragItem } from '../drag-item';
import { DragAndDropMapping } from '../drag-and-drop-mapping';

export class DragAndDropQuestion extends Question {
    constructor(
        public id?: number,
        public backgroundFilePath?: string,
        public dropLocations?: DropLocation[],
        public dragItems?: DragItem[],
        public correctMappings?: DragAndDropMapping[],
    ) {
        super();
    }
}
