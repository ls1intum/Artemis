import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { Participation } from 'app/entities/participation/participation.model';
import { Submission } from 'app/entities/submission.model';
import { Feedback } from 'app/entities/feedback.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

export class Result implements BaseEntity {
    public id?: number;
    public completionDate?: dayjs.Dayjs;
    public successful?: boolean;
    public hasFeedback?: boolean;

    /**
     * Current score in percent i.e. between 1 - 100
     * - Can be larger than 100 if bonus points are available
     */
    public score?: number;
    public assessmentType?: AssessmentType;
    public rated?: boolean;
    public hasComplaint?: boolean;
    public exampleResult?: boolean;
    public testCaseCount?: number;
    public passedTestCaseCount?: number;
    public codeIssueCount?: number;

    public submission?: Submission;
    public assessor?: User;
    public feedbacks?: Feedback[];
    public participation?: Participation;

    // helper attributes
    public durationInMinutes?: number;

    constructor() {
        this.successful = false; // default value
    }

    /**
     * Checks whether the result is a manual result. A manual result can be from type MANUAL or SEMI_AUTOMATIC
     *
     * @return true if the result is a manual result
     */
    public static isManualResult(that: Result): boolean {
        return that.assessmentType === AssessmentType.MANUAL || that.assessmentType === AssessmentType.SEMI_AUTOMATIC;
    }
}
