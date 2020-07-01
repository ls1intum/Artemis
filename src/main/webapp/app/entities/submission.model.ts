import { BaseEntity } from 'app/shared/model/base-entity';
import { Participation } from 'app/entities/participation/participation.model';
import { Language } from 'app/entities/tutor-group.model';
import { Result } from 'app/entities/result.model';
import * as moment from 'moment';

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
    public submissionDate: moment.Moment | null;
    public type: SubmissionType;
    public exampleSubmission: boolean;
    public submissionExerciseType: SubmissionExerciseType;
    public durationInMinutes: number | null;

    public result: Result;
    public participation: Participation;

    public language: Language | null;

    // only used for exam to check if it is saved to server
    public isSynced?: boolean;

    protected constructor(submissionExerciseType: SubmissionExerciseType) {
        this.submissionExerciseType = submissionExerciseType;
    }
}
