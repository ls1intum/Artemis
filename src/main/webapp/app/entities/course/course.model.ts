import { BaseEntity } from './../../shared';
import { Exercise } from '../exercise';

export class Course implements BaseEntity {

    public id: number;
    public title: string;
    public studentGroupName: string;
    public teachingAssistantGroupName: string;
    public instructorGroupName: string;
    public startDate: any;
    public endDate: any;
    public onlineCourse = false;       // default value
    public exercises: Exercise[];

    // helper attributes
    public isAtLeastTutor: boolean;

    constructor() {
    }
}
