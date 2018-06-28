import { BaseEntity } from './../../shared';

export class DragItem implements BaseEntity {
    constructor(
        public id?: number,
        public pictureFilePath?: string,
        public text?: string,
        public correctScore?: number,
        public incorrectScore?: number,
        public correctLocation?: BaseEntity,
        public question?: BaseEntity,
    ) {
    }
}
