import { BaseEntity } from './../../shared';

export class Exercise implements BaseEntity {
    constructor(
        public id?: number,
        public title?: string,
        public releaseDate?: any,
        public dueDate?: any,
        public participations?: BaseEntity[],
        public course?: BaseEntity,
    ) {
    }
}
