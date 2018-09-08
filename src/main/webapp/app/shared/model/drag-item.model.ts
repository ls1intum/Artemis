import { IDropLocation } from 'app/shared/model/drop-location.model';
import { IDragAndDropQuestion } from 'app/shared/model/drag-and-drop-question.model';

export interface IDragItem {
    id?: number;
    pictureFilePath?: string;
    text?: string;
    correctScore?: number;
    incorrectScore?: number;
    correctLocation?: IDropLocation;
    question?: IDragAndDropQuestion;
}

export class DragItem implements IDragItem {
    constructor(
        public id?: number,
        public pictureFilePath?: string,
        public text?: string,
        public correctScore?: number,
        public incorrectScore?: number,
        public correctLocation?: IDropLocation,
        public question?: IDragAndDropQuestion
    ) {}
}
