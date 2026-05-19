export enum CompetencyOrchestrationStatus {
    Success = 'SUCCESS',
    Partial = 'PARTIAL',
    Failed = 'FAILED',
    InProgress = 'IN_PROGRESS',
}

export enum CompetencyOrchestrationFailureReason {
    NoChatClient = 'NO_CHAT_CLIENT',
    LlmError = 'LLM_ERROR',
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
    competencyId: number;
    competencyTitle: string;
    exerciseId?: number;
    weight?: number;
    detail: string;
    justification: string;
}

export interface CompetencyOrchestrationResultDTO {
    status: CompetencyOrchestrationStatus;
    summary?: string;
    appliedActions?: AppliedActionDTO[];
    failureReason?: CompetencyOrchestrationFailureReason;
}
