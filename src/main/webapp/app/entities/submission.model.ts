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

    // latestResult is undefined until setLatestSubmissionResult() is called
    public latestResult?: undefined | Result;

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
 * Used to set / override the latest result in the results list, and set / override the
 * var latestResult
 *
 * @param submission
 * @param result
 */
export function setLatestSubmissionResult(submission: Submission | undefined, result: Result | undefined) {
    if (!submission || !result) {
        return;
    }

    if (submission.results && submission.results.length > 0) {
        submission.results[submission.results.length - 1] = result;
    } else {
        submission.results = [result];
    }
    submission.latestResult = result;
}

export function getFirstResult(submission: Submission | undefined): Result | undefined {
    if (submission?.results) {
        const length = submission.results.length;
        if (length > 0) {
            return submission.results[0];
        }
    }
}
