import { BaseEntity } from 'app/shared';
import { Result } from '../result';
import { Participation } from '../participation';
import { Moment } from 'moment';
import { Language } from 'app/entities/tutor-group';

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
    public id: number;
    public submitted = false; // default value
    public submissionDate: Moment | null;
    public type: SubmissionType;
    public exampleSubmission: boolean;
    public submissionExerciseType: SubmissionExerciseType;

    public result: Result;
    public participation: Participation;

    public language: Language | null;

    protected constructor(submissionExerciseType: SubmissionExerciseType) {
        this.submissionExerciseType = submissionExerciseType;
    }
}
