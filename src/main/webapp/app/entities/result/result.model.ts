import { User } from 'app/core';
import { BaseEntity } from 'app/shared';
import { Feedback } from '../feedback';
import { Submission } from '../submission';
import { Participation } from '../participation';
import { Moment } from 'moment';
import { AssessmentType } from 'app/entities/assessment-type';

export class Result implements BaseEntity {
    public id: number;
    public resultString: string;
    public completionDate: Moment | null;
    public successful = false; // default value
    public buildArtifact = false; // default value (whether the result includes a build artifact or not, only used in programming exercises)
    public hasFeedback: boolean;
    public score: number;
    public assessmentType: AssessmentType;
    public rated: boolean;
    public hasComplaint: boolean;
    public exampleResult: boolean;

    public submission: Submission | null;
    public assessor: User;
    public feedbacks: Feedback[];
    public participation: Participation | null;

    // helper attributes
    public durationInMinutes: number;

    constructor() {}
}
