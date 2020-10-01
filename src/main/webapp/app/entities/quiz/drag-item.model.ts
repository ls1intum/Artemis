import { BaseEntity } from 'app/shared/model/base-entity';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { generate } from 'app/exercises/quiz/manage/temp-id';

export class DragItem implements BaseEntity {
    public id?: number;
    public tempID?: number;
    public pictureFilePath?: string;
    public text?: string;
    public question?: DragAndDropQuestion;
    public invalid?: boolean;

    constructor() {
        this.tempID = generate();
        this.invalid = false; // default value
    }
}
