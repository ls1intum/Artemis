export enum IrisRunState {
    RUNNING = 'RUNNING',
    FINISHED = 'FINISHED',
    FAILED = 'FAILED',
}

export enum IrisActivityKind {
    TOOL = 'TOOL',
    COMMAND = 'COMMAND',
}

export enum IrisActivityState {
    RUNNING = 'RUNNING',
    FINISHED = 'FINISHED',
    FAILED = 'FAILED',
}

export interface IrisActivityItem {
    id: string;
    kind: IrisActivityKind;
    name: string;
    state: IrisActivityState;
    detail?: string;
    result?: string;
    durationMillis?: number;
}

export interface IrisStatusError {
    message?: string;
    code?: string;
}
