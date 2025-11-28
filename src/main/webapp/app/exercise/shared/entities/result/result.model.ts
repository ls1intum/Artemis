import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { AssessmentNote } from 'app/assessment/shared/entities/assessment-note.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

export class Result implements BaseEntity {
    public id?: number;
    public completionDate?: dayjs.Dayjs;
    public successful?: boolean;

    /**
     * Current score in percent i.e. between 1 - 100
     * - Can be larger than 100 if bonus points are available
     */
    public score?: number;
    public assessmentType?: AssessmentType;
    public rated?: boolean;
    public hasComplaint?: boolean;
    public exampleResult?: boolean;
    public assessmentNote?: AssessmentNote;
    public testCaseCount?: number;
    public passedTestCaseCount?: number;
    public codeIssueCount?: number;
    public buildJobId?: string;

    public submission?: Submission;
    public assessor?: User;
    public feedbacks?: Feedback[];

    // helper attributes
    public durationInMinutes?: number;

    constructor() {
        this.successful = false; // default value
    }

    /**
     * Checks whether the given result has an assessment note that is not empty.
     * @param that the result of which the presence of an assessment note is being checked
     *
     * @return true if the assessment note exists and is not empty, false otherwise
     */
    public static hasNonEmptyAssessmentNote(that: Result | undefined): boolean {
        return that ? !!that.assessmentNote?.note?.length : false;
    }
}
/**
 * Checks whether the given result is a practice result.
 * A practice result is a result that belongs to a test run of a student participation.
 * @param result the result to check
 * @return true if the result is a practice result, false otherwise
 */
export function isPracticeResult(result: Result | undefined): boolean {
    if (result?.submission?.participation instanceof StudentParticipation) {
        return result.submission.participation.testRun ?? false;
    }
    return false;
}
