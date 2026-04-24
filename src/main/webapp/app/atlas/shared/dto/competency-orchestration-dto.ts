export enum CompetencyOrchestrationStatus {
    Success = 'SUCCESS',
    Preview = 'PREVIEW',
    Failed = 'FAILED',
    InProgress = 'IN_PROGRESS',
}

export enum AppliedActionType {
    Create = 'CREATE',
    Edit = 'EDIT',
    Assign = 'ASSIGN',
    Unassign = 'UNASSIGN',
    Delete = 'DELETE',
}

export interface AppliedActionDTO {
    type: AppliedActionType;
    competencyId?: number;
    competencyTitle?: string;
    exerciseId?: number;
    weight?: number;
    detail?: string;
    justification?: string;
}

export interface CompetencyOrchestrationResultDTO {
    status: CompetencyOrchestrationStatus;
    message?: string;
    appliedActions?: AppliedActionDTO[];
}
