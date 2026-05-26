export enum CompetencyOrchestrationStatus {
    Success = 'SUCCESS',
    Failed = 'FAILED',
    InProgress = 'IN_PROGRESS',
}

export enum CompetencyOrchestrationFailureReason {
    NoChatClient = 'NO_CHAT_CLIENT',
    LlmError = 'LLM_ERROR',
    UnsupportedExercise = 'UNSUPPORTED_EXERCISE',
}

export interface CompetencyOrchestrationResultDTO {
    status: CompetencyOrchestrationStatus;
    summary: string;
    failureReason?: CompetencyOrchestrationFailureReason;
}
