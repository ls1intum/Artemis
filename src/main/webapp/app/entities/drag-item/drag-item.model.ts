import { BaseEntity } from 'app/shared/model/base-entity';
import { generate } from 'app/quiz/edit/temp-id';
import { DragAndDropQuestion } from 'app/entities/drag-and-drop-question/drag-and-drop-question.model';

export class DragItem implements BaseEntity {
    public id: number;
    public tempID: number;
    public pictureFilePath: string | null;
    public text: string | null;
    public question: DragAndDropQuestion;
    public invalid = false; // default value

    constructor() {
        this.tempID = generate();
    }
}
