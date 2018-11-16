import { BaseEntity } from 'app/shared';
import { Exercise } from '../exercise';
import { Moment } from 'moment';

export class Course implements BaseEntity {
    public id: number;
    public title: string;
    public shortName: string;
    public studentGroupName: string;
    public teachingAssistantGroupName: string;
    public instructorGroupName: string;
    public startDate: Moment;
    public endDate: Moment;
    public onlineCourse = false; // default value
    public exercises: Exercise[];

    // helper attributes
    public isAtLeastTutor: boolean;

    constructor() {}
}
