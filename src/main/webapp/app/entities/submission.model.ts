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

    public results?: Result[];
    public participation?: Participation;

    // only used for exam to check if it is saved to server
    public isSynced?: boolean;

    protected constructor(submissionExerciseType: SubmissionExerciseType) {
        this.submissionExerciseType = submissionExerciseType;
        this.submitted = false; // default value
    }
}

export function getLatestSubmissionResult(submission: Submission | undefined): Result | undefined {
    checkForResultsLength(submission);
    if (submission?.results) {
        const length = submission.results.length;
        if (length > 0) {
            return submission.results[length - 1];
        }
    }
}

export function getFirstResult(submission: Submission | undefined): Result | undefined {
    checkForResultsLength(submission);
    if (submission?.results) {
        const length = submission.results.length;
        if (length > 0) {
            return submission.results[0];
        }
    }
}

//todo NR: remove this function once PR can be merged into develop
function checkForResultsLength(submission: Submission | undefined) {
    if (submission!.results && submission!.results?.length > 1) {
        console.error('Multiple results for submission ' + 'are currently not supported! Submission.results: ', submission?.results);
    }
}
