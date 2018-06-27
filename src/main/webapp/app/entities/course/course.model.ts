import { BaseEntity } from './../../shared';
import { Exercise } from '../exercise';

export class Course implements BaseEntity {
    constructor(
        public id?: number,
        public title?: string,
        public studentGroupName?: string,
        public teachingAssistantGroupName?: string,
        public instructorGroupName?: string,
        public startDate?: any,
        public endDate?: any,
        public onlineCourse?: boolean,
        public exercises?: Exercise[],
    ) {
        this.onlineCourse = false;
    }
}
