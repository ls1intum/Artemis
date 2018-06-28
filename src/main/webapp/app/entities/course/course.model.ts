import { BaseEntity } from './../../shared';

export class Course implements BaseEntity {
    constructor(
        public id?: number,
        public title?: string,
        public studentGroupName?: string,
        public teachingAssistantGroupName?: string,
        public exercises?: BaseEntity[],
    ) {
    }
}
