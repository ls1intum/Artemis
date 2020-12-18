import { BaseEntity } from 'app/shared/model/base-entity';
import { Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { Moment } from 'moment';

export const enum SubmissionType {
    MANUAL = 'MANUAL',
    TIMEOUT = 'TIMEOUT',
    INSTRUCTOR = 'INSTRUCTOR',
    EXTERNAL = 'EXTERNAL',
    TEST = 'TEST',
}

// IMPORTANT NOTICE: The following strings have to be consistent with the ones defined in Submission.java
export const enum SubmissionExerciseType {
    PROGRAMMING = 'programming',
    MODELING = 'modeling',
    QUIZ = 'quiz',
    TEXT = 'text',
    FILE_UPLOAD = 'file-upload',
}

export abstract class Submission implements BaseEntity {
    public id?: number;
    public submitted?: boolean;
    public submissionDate?: Moment;
    public type?: SubmissionType;
    public exampleSubmission?: boolean;
    public submissionExerciseType?: SubmissionExerciseType;
    public durationInMinutes?: number;

    // results is initialized by the value the server returns
    public results?: Result[];

    public participation?: Participation;

    // Helper Attributes

    // latestResult is undefined until submission.setLatestResult() is called
    public latestResult?: Result | undefined;

    // only used for exam to check if it is saved to server
    public isSynced?: boolean;

    protected constructor(submissionExerciseType: SubmissionExerciseType) {
        this.submissionExerciseType = submissionExerciseType;
        this.submitted = false; // default value
    }
}

/**
 * Used to access the latest submissions result
 *
 * @param submission
 */
export function getLatestSubmissionResult(submission: Submission | undefined): Result | undefined {
    if (submission?.results) {
        const length = submission.results.length;
        if (length > 0) {
            return submission.results[length - 1];
        }
    }
}

/**
 * Used to set the latest submissions result
 *
 * @param submission
 * @param result
 *
 * @return the index of the latestSubmissionResult or undefined if any of the params was undefined
 */
export function setLatestSubmissionResult(submission: Submission | undefined, result: Result | undefined): number | undefined {
    if (!submission || !result) {
        return undefined;
    }

    if (submission!.results && submission!.results.length > 0) {
        submission.results[submission.results.length - 1] = result;
    } else {
        submission.results = [result];
    }
    return submission.results.length - 1;
}

export function getFirstResult(submission: Submission | undefined): Result | undefined {
    if (submission?.results) {
        const length = submission.results.length;
        if (length > 0) {
            return submission.results[0];
        }
    }
}

export function setLatestResult(submissions: Submission[]) {
    submissions.forEach((submission) => {
        submission.latestResult = getLatestSubmissionResult(submission);
    });
}
