import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';

export class DragAndDropContent {
    public dropLocations?: DropLocation[];
    public dragItems?: DragItem[];
    public correctMappings?: DragAndDropMapping[];
}
