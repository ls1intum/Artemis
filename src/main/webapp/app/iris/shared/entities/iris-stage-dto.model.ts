export class IrisStageDTO {
    name: string;
    weight: number;
    state: IrisStageStateDTO;
    message: string;

    lowerCaseState?: string;
}

export enum IrisStageStateDTO {
    NOT_STARTED = 'NOT_STARTED',
    IN_PROGRESS = 'IN_PROGRESS',
    DONE = 'DONE',
    SKIPPED = 'SKIPPED',
    ERROR = 'ERROR',
}
