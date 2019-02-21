import { BaseEntity } from 'app/shared';
import { Exercise } from '../exercise';
import { Moment } from 'moment';

export class Course implements BaseEntity {
    public id: number;
    public title: string;
    public description: string;
    public shortName: string;
    public studentGroupName: string;
    public teachingAssistantGroupName: string;
    public instructorGroupName: string;
    public startDate: Moment;
    public endDate: Moment;
    public onlineCourse = false; // default value
    public maxComplaints: number;

    public exercises: Exercise[];

    // helper attributes
    public isAtLeastTutor: boolean;
    public relativeScore: number;
    public absoluteScore: number;
    public maxScore: number;

    constructor() {}
}
