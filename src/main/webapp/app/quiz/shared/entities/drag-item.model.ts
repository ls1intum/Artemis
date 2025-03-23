import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { BaseEntityWithTempId, CanBecomeInvalid } from 'app/quiz/shared/entities/drop-location.model';

export class DragItem extends BaseEntityWithTempId implements CanBecomeInvalid {
    public pictureFilePath?: string;
    public text?: string;
    public question?: DragAndDropQuestion;
    public invalid = false; // default value

    constructor() {
        super();
    }
}
