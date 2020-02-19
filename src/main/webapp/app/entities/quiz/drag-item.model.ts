import { BaseEntity } from 'app/shared/model/base-entity';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { generate } from 'app/exercises/quiz/manage/temp-id';

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
