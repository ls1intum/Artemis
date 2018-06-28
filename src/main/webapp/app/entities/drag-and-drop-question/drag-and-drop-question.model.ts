import { BaseEntity } from './../../shared';

export class DragAndDropQuestion implements BaseEntity {
    constructor(
        public id?: number,
        public backgroundFilePath?: string,
        public dropLocations?: BaseEntity[],
        public dragItems?: BaseEntity[],
    ) {
    }
}
