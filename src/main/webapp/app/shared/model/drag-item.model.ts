import { IDragAndDropQuestion } from 'app/shared/model//drag-and-drop-question.model';

export interface IDragItem {
    id?: number;
    pictureFilePath?: string;
    text?: string;
    invalid?: boolean;
    question?: IDragAndDropQuestion;
}

export class DragItem implements IDragItem {
    constructor(
        public id?: number,
        public pictureFilePath?: string,
        public text?: string,
        public invalid?: boolean,
        public question?: IDragAndDropQuestion
    ) {
        this.invalid = this.invalid || false;
    }
}
