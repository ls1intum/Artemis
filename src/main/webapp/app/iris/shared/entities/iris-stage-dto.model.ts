export class IrisStageDTO {
    name: string;
    weight: number;
    state: IrisStageStateDTO;
    message: string;
    // Internal stages are not shown in the UI and are hidden from the user
    internal: boolean;

    lowerCaseState?: string;
}

export enum IrisStageStateDTO {
    NOT_STARTED = 'NOT_STARTED',
    IN_PROGRESS = 'IN_PROGRESS',
    DONE = 'DONE',
    SKIPPED = 'SKIPPED',
    ERROR = 'ERROR',
}
