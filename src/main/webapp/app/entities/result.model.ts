import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Moment } from 'moment';
import { Participation } from 'app/entities/participation/participation.model';
import { Submission } from 'app/entities/submission.model';
import { Feedback } from 'app/entities/feedback.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

export class Result implements BaseEntity {
    public id: number;
    public resultString: string;
    public completionDate: Moment | null;
    public successful = false; // default value
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
