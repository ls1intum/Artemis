import { BaseEntity } from 'app/shared/model/base-entity';

export enum IrisEventSessionType {
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
    enabled = false;
    selectedEventVariant: string;
    sessionType: IrisEventSessionType;
}
export class JolEventSettings extends IrisEventSettings {
    sessionType = IrisEventSessionType.COURSE;
    type = IrisEventType.JOL;
    selectedEventVariant = 'jol';
}

export class ProgressStalledEventSettings extends IrisEventSettings {
    sessionType = IrisEventSessionType.COURSE;
    type = IrisEventType.PROGRESS_STALLED;
    selectedEventVariant = 'progress_stalled';
}

export class BuildFailedEventSettings extends IrisEventSettings {
    sessionType = IrisEventSessionType.EXERCISE;
    type = IrisEventType.BUILD_FAILED;
    selectedEventVariant = 'build_failed';
}
