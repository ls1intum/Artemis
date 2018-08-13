import { BaseEntity } from './../../shared';
import { DragAndDropQuestion } from 'app/entities/drag-and-drop-question';

export class DragItem implements BaseEntity {
    constructor(
        public id?: number,
        public tempID?: number,
        public pictureFilePath?: string,
        public text?: string,
        public correctScore?: number,
        public incorrectScore?: number,
        public question?: DragAndDropQuestion,
        public invalid?: boolean,
    ) {
    }
}
