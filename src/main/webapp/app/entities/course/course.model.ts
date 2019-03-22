import { BaseEntity } from 'app/shared';
import { Exercise } from '../exercise';
import { Moment } from 'moment';
import { Lecture } from 'app/entities/lecture';
import { TutorGroup } from 'app/entities/tutor-group';

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
    public color: string;
    public onlineCourse = false; // default value
    public registrationEnabled = false; // default value
    public maxComplaints: number;

    public exercises: Exercise[];
    public lectures: Lecture[];
    public tutorGroups: TutorGroup[];

    // helper attributes
    public isAtLeastTutor: boolean;
    public relativeScore: number;
    public absoluteScore: number;
    public maxScore: number;

    constructor() {}
}

export type StatsForInstructorDashboard = {
    numberOfStudents: number;
    numberOfSubmissions: number;
    numberOfTutors: number;
    numberOfAssessments: number;
    numberOfComplaints: number;
    numberOfOpenComplaints: number;
};

export type StatsForTutorDashboard = {
    numberOfAssessments: number;
    numberOfTutorAssessments: number;
    numberOfComplaints: number;
    numberOfSubmissions: number;
};
