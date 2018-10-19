import { IDragAndDropQuestion } from 'app/shared/model//drag-and-drop-question.model';

export interface IDropLocation {
    id?: number;
    posX?: number;
    posY?: number;
    width?: number;
    height?: number;
    invalid?: boolean;
    question?: IDragAndDropQuestion;
}

export class DropLocation implements IDropLocation {
    constructor(
        public id?: number,
        public posX?: number,
        public posY?: number,
        public width?: number,
        public height?: number,
        public invalid?: boolean,
        public question?: IDragAndDropQuestion
    ) {
        this.invalid = this.invalid || false;
    }
}
