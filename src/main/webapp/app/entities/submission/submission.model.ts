import { BaseEntity } from './../../shared';
import { Result } from '../result';
import { Participation } from '../participation';
import { ExerciseType } from 'app/entities/exercise';

export const enum SubmissionType {
    MANUAL = 'MANUAL',
    TIMEOUT = 'TIMEOUT'
}

// IMPORTANT NOTICE: The following strings have to be consistent with the ones defined in Submission.java
export const enum SubmissionExerciseType {
    PROGRAMMING = 'programming',
    MODELING = 'modeling',
    QUIZ = 'quiz',
    TEXT = 'text',
    FILE_UPLOAD = 'file-upload'
}

export abstract class Submission implements BaseEntity {

    public id: number;
    public submitted = false;   // default value
    public submissionDate: any;
    public type: SubmissionType;
    public result: Result;
    public participation: Participation;
    public submissionExerciseType: SubmissionExerciseType;

    constructor(submissionExerciseType: SubmissionExerciseType) {
        this.submissionExerciseType = submissionExerciseType;
    }
}
