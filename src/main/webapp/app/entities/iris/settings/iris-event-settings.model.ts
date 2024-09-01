import { BaseEntity } from 'app/shared/model/base-entity';

export enum IrisEventLevel {
    COURSE = 'COURSE',
    EXERCISE = 'EXERCISE',
}

export enum IrisEventType {
    JOL = 'jol',
    PROGRESS_STALLED = 'progress_stalled',
    BUILD_FAILED = 'build_failed',
}

export class IrisEventSettings implements BaseEntity {
    id?: number;
    type: IrisEventType;
    active = false;
    pipelineVariant: string;
    target: IrisEventLevel;
}
export class JolEventSettings extends IrisEventSettings {
    target = IrisEventLevel.COURSE;
    type = IrisEventType.JOL;
    pipelineVariant = 'jol';
}

export class ProgressStalledEventSettings extends IrisEventSettings {
    target = IrisEventLevel.COURSE;
    type = IrisEventType.PROGRESS_STALLED;
    pipelineVariant = 'progress_stalled';
}

export class BuildFailedEventSettings extends IrisEventSettings {
    target = IrisEventLevel.EXERCISE;
    type = IrisEventType.BUILD_FAILED;
    pipelineVariant = 'build_failed';
}
