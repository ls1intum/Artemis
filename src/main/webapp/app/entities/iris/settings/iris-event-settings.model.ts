import { BaseEntity } from 'app/shared/model/base-entity';

export enum IrisEventLevel {
    COURSE = 'COURSE',
    EXERCISE = 'EXERCISE',
}

export enum IrisEventType {
    JOL = 'jol',
    SUBMISSION_SUCCESSFUL = 'submission_successful',
    SUBMISSION_FAILED = 'submission_failed',
}

export class IrisEventSettings implements BaseEntity {
    id?: number;
    type: IrisEventType;
    active = false;
    deferredUntil?: Date;
    pipelineVariant: string;
    priority: number;
    level: IrisEventLevel;
    numberOfFailedAttempts?: number;
    successThreshold?: number;
}
export class JolEventSettings extends IrisEventSettings {
    level = IrisEventLevel.COURSE;
    type = IrisEventType.JOL;
    pipelineVariant = 'jol';
    priority = 1;
    successThreshold = 0;
}

export class SubmissionSuccessfulEventSettings extends IrisEventSettings {
    level = IrisEventLevel.COURSE;
    type = IrisEventType.SUBMISSION_SUCCESSFUL;
    pipelineVariant = 'submission_successful';
    priority = 2;
    successThreshold = 80.0;
}

export class SubmissionFailedEventSettings extends IrisEventSettings {
    level = IrisEventLevel.EXERCISE;
    type = IrisEventType.SUBMISSION_FAILED;
    pipelineVariant = 'submission_failed';
    priority = 0;
    numberOfFailedAttempts = 3;
    successThreshold = 80.0;
}
