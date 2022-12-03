import { BaseEntity } from 'app/shared/model/base-entity';
import { Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';

export const enum SubmissionType {
    MANUAL = 'MANUAL',
    TIMEOUT = 'TIMEOUT',
    INSTRUCTOR = 'INSTRUCTOR',
    EXTERNAL = 'EXTERNAL',
    TEST = 'TEST',
    ILLEGAL = 'ILLEGAL',
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
    public submissionDate?: dayjs.Dayjs;
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

    // client-side property, shows the number of elements used in the example submission
    public submissionSize?: number;

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
    return submission?.results?.last();
}

/**
 * Used to access a submissions result for a specific correctionRound
 *
 * @param submission
 * @param correctionRound
 * @returns the results or undefined if submission or the result for the requested correctionRound is undefined
 */
export function getSubmissionResultByCorrectionRound(submission: Submission | undefined, correctionRound: number): Result | undefined {
    if (submission?.results && submission?.results.length >= correctionRound) {
        return submission.results[correctionRound];
    }
    return undefined;
}

/**
 * Used to access a submissions result for a specific id
 *
 * @param submission
 * @param resultId
 * @returns the results or undefined if submission or the result for the requested id is undefined
 */
export function getSubmissionResultById(submission: Submission | undefined, resultId: number): Result | undefined {
    return submission?.results?.find((result) => result.id === resultId);
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

    if (submission.results?.length) {
        submission.results[submission.results.length - 1] = result;
    } else {
        submission.results = [result];
    }
    // make sure relationship is correct
    result.submission = submission;
    submission.latestResult = result;
}

export function setSubmissionResultByCorrectionRound(submission: Submission, result: Result, correctionRound: number) {
    if (!submission || !result || !submission.results) {
        return;
    }
    submission.results[correctionRound] = result;

    if (submission.results.length === correctionRound + 1) {
        submission.latestResult = result;
    }
}

export function getFirstResult(submission: Submission | undefined): Result | undefined {
    if (submission?.results) {
        const length = submission.results.length;
        if (length > 0) {
            return submission.results[0];
        }
    }
}

export function getFirstResultWithComplaintFromResults(results: Result[] | undefined): Result | undefined {
    if (results) {
        const resultsWithComplaint = results.filter((result) => result.hasComplaint);
        if (resultsWithComplaint.length > 0) {
            return resultsWithComplaint[0];
        }
    }
}

export function getFirstResultWithComplaint(submission: Submission | undefined): Result | undefined {
    if (submission?.results) {
        const resultsWithComplaint = submission.results.filter((result) => result.hasComplaint);
        if (resultsWithComplaint.length > 0) {
            return resultsWithComplaint[0];
        }
    }
}

export function reconnectSubmissions(submissions: Submission[]): void {
    return submissions.forEach((submission: Submission) => {
        // reconnect some associations
        const latestResult = getLatestSubmissionResult(submission);
        if (latestResult) {
            latestResult.submission = submission;
            latestResult.participation = submission.participation;
            submission.participation!.results = [latestResult!];
            setLatestSubmissionResult(submission, latestResult);
        }
    });
}
